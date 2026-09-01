/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zhejianglab.astro.atlas.scanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.zhejianglab.astro.atlas.core.CoverageLayer;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.ScanPlanValidator;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

public final class ScanService {
  private static final int MAX_PENDING_RECORDS = 500;
  private static final Duration DEFAULT_LEASE_DURATION = Duration.ofHours(1);
  private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMinutes(5);
  private final SourceAdapter source;
  private final IndexWriter writer;
  private final EvidenceWriter evidenceWriter = new EvidenceWriter();
  private final Clock clock;
  private final Duration leaseDuration;
  private final Duration heartbeatInterval;

  public ScanService(SourceAdapter source, IndexWriter writer) {
    this(source, writer, Clock.systemUTC(), DEFAULT_LEASE_DURATION, DEFAULT_HEARTBEAT_INTERVAL);
  }

  ScanService(SourceAdapter source, IndexWriter writer, Clock clock,
      Duration leaseDuration, Duration heartbeatInterval) {
    this.source = source;
    this.writer = writer;
    this.clock = clock;
    this.leaseDuration = leaseDuration;
    this.heartbeatInterval = heartbeatInterval;
    if (source == null || writer == null || clock == null) throw new IllegalArgumentException("scan dependencies are required");
    if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
      throw new IllegalArgumentException("leaseDuration must be positive");
    }
    if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()
        || heartbeatInterval.compareTo(leaseDuration) >= 0) {
      throw new IllegalArgumentException("heartbeatInterval must be positive and shorter than leaseDuration");
    }
  }

  public ScanSummary scan(ScanPlan plan) {
    return scan(plan, false);
  }

  public ScanSummary scan(ScanPlan plan, boolean memoryMode) {
    ScanPlanValidator.validate(plan, memoryMode);
    EvidenceWriter.Session evidence = memoryMode ? null
        : evidenceWriter.start(Path.of(plan.evidence().outputPath()), plan.scanRunId(), plan.layer().layerId());
    SnapshotHash memorySnapshot = new SnapshotHash();
    ErrorAccumulator errors = new ErrorAccumulator();
    ScanProgress progress = new ScanProgress();
    CoverageLayer updating = CoverageLayer.updating(plan.layer(), plan.scanRunId(), clock.instant().plus(leaseDuration));
    if (!writer.tryBeginLayerUpdate(updating)) {
      if (evidence != null) {
        try {
          evidence.fail("layer update is already in progress: " + plan.layer().layerId(), List.of(), 0, 0, 0);
        } catch (RuntimeException ignored) {
          // Preserve the lease conflict as the public failure.
        }
        evidence.close();
      }
      throw new IllegalStateException("layer update is already in progress: " + plan.layer().layerId());
    }

    List<FileAsset> pendingFiles = new ArrayList<>();
    List<SpatialCoverage> pendingCoverages = new ArrayList<>();
    LeaseTracker leaseTracker = new LeaseTracker();
    AtomicReference<CoverageLayer> lease = new AtomicReference<>(updating);
    AtomicBoolean completed = new AtomicBoolean(false);
    Thread shutdownHook = new Thread(() -> failOnShutdown(evidence, memorySnapshot, errors, progress, lease,
        completed), "atlas-scan-shutdown");
    try {
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      if (evidence != null) evidence.phase("ENUMERATING");
      try (Stream<org.zhejianglab.astro.atlas.core.InputItem> items = source.enumerate(plan)) {
        Iterator<org.zhejianglab.astro.atlas.core.InputItem> iterator = items.iterator();
        if (evidence != null) evidence.phase("DELETING_LAYER_COVERAGE");
        writer.deleteCoverageForLayer(plan.layer().layerId());
        CoverageExtractor extractor = CoverageExtractorResolver.resolve(plan.extraction());
        if (evidence != null) evidence.phase("EXTRACTING");
        while (iterator.hasNext()) {
          lease.set(leaseTracker.maybeRenew(lease.get()));
          var item = iterator.next();
          progress.discovered();
          FileAsset fileAsset = FileAsset.from(item);
          ExtractionResult result;
          try {
            result = extractor.extract(item, source.open(item), plan);
          } catch (RuntimeException exception) {
            result = new ExtractionResult(List.of(), List.of(message(exception)), 0, 0, 0);
          }
          if (evidence != null) evidence.record(fileAsset, result.coverages());
          else memorySnapshot.add(fileAsset);
          for (String error : result.errors()) {
            errors.add(error);
            if (evidence != null) evidence.error(error);
          }
          pendingFiles.add(fileAsset);
          pendingCoverages.addAll(result.coverages());
          progress.processed(result);
          boolean extractionFailed = !result.errors().isEmpty();
          if (extractionFailed || pendingFiles.size() + pendingCoverages.size() >= MAX_PENDING_RECORDS) {
            lease.set(leaseTracker.maybeRenew(lease.get()));
            if (evidence != null) evidence.phase("WRITING");
            writer.upsertBatch(pendingFiles, pendingCoverages);
            pendingFiles = new ArrayList<>();
            pendingCoverages = new ArrayList<>();
          }
          if (extractionFailed) {
            throw new IllegalStateException("scan produced extraction errors: " + errors.first());
          }
        }
      }
      lease.set(leaseTracker.maybeRenew(lease.get()));
      if (evidence != null) evidence.phase("WRITING");
      if (!pendingFiles.isEmpty() || !pendingCoverages.isEmpty()) {
        writer.upsertBatch(pendingFiles, pendingCoverages);
      }
      ScanProgress.Snapshot snapshot = progress.snapshot();
      EvidenceWriter.EvidenceResult result = evidence == null
          ? new EvidenceWriter.EvidenceResult(memorySnapshot.finish(), null)
          : evidence.complete(snapshot.orders(), snapshot.catalogRows(), snapshot.validCatalogRows(), snapshot.invalidCatalogRows());
      CoverageLayer active = lease.get().active(result.snapshotSha256(), snapshot.orders(), snapshot.fileCount(),
          snapshot.coverageCount(), 0);
      if (!writer.finishLayerUpdate(active)) {
        throw new IllegalStateException("layer lease was lost before activation: " + plan.layer().layerId());
      }
      completed.set(true);
      return new ScanSummary("COMPLETED", plan.scanRunId(), plan.layer().layerId(), result.snapshotSha256(),
          snapshot.discovered(), snapshot.processed(), snapshot.coverageCount(), snapshot.catalogRows(),
          snapshot.validCatalogRows(), snapshot.invalidCatalogRows(), 0, snapshot.orders(), result.path(), Instant.now());
    } catch (RuntimeException exception) {
      String failure = message(exception);
      if (errors.count() == 0) errors.add(failure);
      ScanProgress.Snapshot snapshot = progress.snapshot();
      String snapshotHash;
      if (evidence != null) {
        try {
          EvidenceWriter.EvidenceResult result = evidence.fail(failure, snapshot.orders(), snapshot.catalogRows(),
              snapshot.validCatalogRows(), snapshot.invalidCatalogRows());
          snapshotHash = result.snapshotSha256();
        } catch (RuntimeException evidenceFailure) {
          exception.addSuppressed(evidenceFailure);
          snapshotHash = evidence.snapshotSha256();
        }
      } else {
        snapshotHash = memorySnapshot.finish();
      }
      try {
        writer.finishLayerUpdate(lease.get().failed(errors.first(), snapshotHash, errors.count()));
      } catch (RuntimeException ignored) {
        // Preserve the original failure; the adapter may be unavailable too.
      }
      completed.set(true);
      throw exception;
    } finally {
      removeShutdownHook(shutdownHook);
      if (evidence != null) evidence.close();
    }
  }

  private void failOnShutdown(EvidenceWriter.Session evidence, SnapshotHash memorySnapshot,
      ErrorAccumulator errors, ScanProgress progress, AtomicReference<CoverageLayer> lease,
      AtomicBoolean completed) {
    if (!completed.compareAndSet(false, true)) return;
    String failure = "scanner process terminated before scan completed";
    errors.add(failure);
    ScanProgress.Snapshot snapshot = progress.snapshot();
    String snapshotHash = null;
    if (evidence != null) {
      try {
        evidence.error(failure);
      } catch (RuntimeException ignored) {
        // EvidenceWriter's own shutdown hook may have closed the streams first.
      }
      try {
        snapshotHash = evidence.fail(failure, snapshot.orders(), snapshot.catalogRows(), snapshot.validCatalogRows(),
            snapshot.invalidCatalogRows()).snapshotSha256();
      } catch (RuntimeException ignored) {
        snapshotHash = evidence.snapshotSha256();
      }
    } else {
      snapshotHash = memorySnapshot.finish();
    }
    CoverageLayer current = lease.get();
    if (current == null) return;
    try {
      writer.finishLayerUpdate(current.failed(errors.first(), snapshotHash, errors.count()));
    } catch (RuntimeException ignored) {
      // Preserve the process termination while making the best effort to close the lease.
    }
  }

  private static void removeShutdownHook(Thread hook) {
    try {
      Runtime.getRuntime().removeShutdownHook(hook);
    } catch (IllegalArgumentException | IllegalStateException ignored) {
      // Shutdown is already in progress or the hook was not registered.
    }
  }

  private static String message(Throwable exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }

  private final class LeaseTracker {
    private Instant lastHeartbeat = clock.instant();
    private LeaseTracker() {}

    private CoverageLayer maybeRenew(CoverageLayer current) {
      Instant now = clock.instant();
      if (now.isBefore(lastHeartbeat.plus(heartbeatInterval))) return current;
      Instant nextExpiry = now.plus(leaseDuration);
      if (!writer.renewLayerUpdate(current.layerId(), current.scanRunId(), nextExpiry)) {
        throw new IllegalStateException("layer lease was lost during scan: " + current.layerId());
      }
      lastHeartbeat = now;
      return current.renewed(nextExpiry);
    }
  }

  private static final class ErrorAccumulator {
    private int count;
    private String first;

    synchronized void add(String value) {
      count++;
      if (first == null || first.isBlank()) first = value == null || value.isBlank() ? "unknown scan error" : value;
    }

    synchronized int count() { return count; }
    synchronized String first() { return first == null ? "unknown scan error" : first; }
  }

  private static final class ScanProgress {
    private final Set<Integer> orders = Collections.synchronizedSet(new LinkedHashSet<>());
    private int discovered;
    private int processed;
    private int fileCount;
    private int coverageCount;
    private int catalogRows;
    private int validCatalogRows;
    private int invalidCatalogRows;

    synchronized void discovered() { discovered++; }

    synchronized void processed(ExtractionResult result) {
      processed++;
      fileCount++;
      coverageCount += result.coverages().size();
      catalogRows += result.catalogRows();
      validCatalogRows += result.validCatalogRows();
      invalidCatalogRows += result.invalidCatalogRows();
      orders.addAll(result.coverages().stream().map(SpatialCoverage::healpixOrder).toList());
    }

    synchronized Snapshot snapshot() {
      return new Snapshot(discovered, processed, fileCount, coverageCount, catalogRows, validCatalogRows,
          invalidCatalogRows, orders.stream().sorted().toList());
    }

    record Snapshot(int discovered, int processed, int fileCount, int coverageCount, int catalogRows,
        int validCatalogRows, int invalidCatalogRows, List<Integer> orders) {}
  }

  private static final class SnapshotHash {
    private final MessageDigest digest;
    private String value;

    SnapshotHash() {
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException("SHA-256 is unavailable", exception);
      }
    }

    synchronized void add(FileAsset file) {
      if (value != null) return;
      digest.update(file.fileId().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
    }

    synchronized String finish() {
      if (value == null) value = java.util.HexFormat.of().formatHex(digest.digest());
      return value;
    }
  }
}
