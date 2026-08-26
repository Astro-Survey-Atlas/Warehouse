package org.zhejianglab.astro.atlas.scanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    CoverageLayer updating = CoverageLayer.updating(plan.layer(), plan.scanRunId(), clock.instant().plus(leaseDuration));
    if (!writer.tryBeginLayerUpdate(updating)) {
      throw new IllegalStateException("layer update is already in progress: " + plan.layer().layerId());
    }

    EvidenceWriter.Session evidence = null;
    SnapshotHash memorySnapshot = new SnapshotHash();
    ErrorAccumulator errors = new ErrorAccumulator();
    int discovered = 0;
    int processed = 0;
    int fileCount = 0;
    int coverageCount = 0;
    int catalogRows = 0;
    int validCatalogRows = 0;
    int invalidCatalogRows = 0;
    Set<Integer> orders = new LinkedHashSet<>();
    List<FileAsset> pendingFiles = new ArrayList<>();
    List<SpatialCoverage> pendingCoverages = new ArrayList<>();
    LeaseTracker leaseTracker = new LeaseTracker();
    CoverageLayer lease = updating;
    try {
      if (!memoryMode) {
        evidence = evidenceWriter.start(Path.of(plan.evidence().outputPath()), plan.scanRunId(), plan.layer().layerId());
        evidence.phase("ENUMERATING");
      }
      try (Stream<org.zhejianglab.astro.atlas.core.InputItem> items = source.enumerate(plan)) {
        Iterator<org.zhejianglab.astro.atlas.core.InputItem> iterator = items.iterator();
        if (evidence != null) evidence.phase("DELETING_LAYER_COVERAGE");
        writer.deleteCoverageForLayer(plan.layer().layerId());
        CoverageExtractor extractor = CoverageExtractorResolver.resolve(plan.extraction());
        if (evidence != null) evidence.phase("EXTRACTING");
        while (iterator.hasNext()) {
          lease = leaseTracker.maybeRenew(lease);
          var item = iterator.next();
          discovered++;
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
          for (SpatialCoverage coverage : result.coverages()) orders.add(coverage.healpixOrder());
          fileCount++;
          coverageCount += result.coverages().size();
          catalogRows += result.catalogRows();
          validCatalogRows += result.validCatalogRows();
          invalidCatalogRows += result.invalidCatalogRows();
          processed++;
          if (pendingFiles.size() + pendingCoverages.size() >= MAX_PENDING_RECORDS) {
            lease = leaseTracker.maybeRenew(lease);
            if (evidence != null) evidence.phase("WRITING");
            writer.upsertBatch(pendingFiles, pendingCoverages);
            pendingFiles = new ArrayList<>();
            pendingCoverages = new ArrayList<>();
          }
        }
      }
      lease = leaseTracker.maybeRenew(lease);
      if (evidence != null) evidence.phase("WRITING");
      if (!pendingFiles.isEmpty() || !pendingCoverages.isEmpty()) {
        writer.upsertBatch(pendingFiles, pendingCoverages);
      }
      if (errors.count() > 0) {
        throw new IllegalStateException("scan produced extraction errors: " + errors.first());
      }
      EvidenceWriter.EvidenceResult result = evidence == null
          ? new EvidenceWriter.EvidenceResult(memorySnapshot.finish(), null)
          : evidence.complete(sortedOrders(orders), catalogRows, validCatalogRows, invalidCatalogRows);
      CoverageLayer active = lease.active(result.snapshotSha256(), sortedOrders(orders), fileCount, coverageCount, 0);
      if (!writer.finishLayerUpdate(active)) {
        throw new IllegalStateException("layer lease was lost before activation: " + plan.layer().layerId());
      }
      return new ScanSummary("COMPLETED", plan.scanRunId(), plan.layer().layerId(), result.snapshotSha256(),
          discovered, processed, coverageCount, catalogRows, validCatalogRows, invalidCatalogRows,
          0, sortedOrders(orders), result.path(), Instant.now());
    } catch (RuntimeException exception) {
      String failure = message(exception);
      if (errors.count() == 0) errors.add(failure);
      String snapshot;
      if (evidence != null) {
        try {
          EvidenceWriter.EvidenceResult result = evidence.fail(failure, sortedOrders(orders), catalogRows,
              validCatalogRows, invalidCatalogRows);
          snapshot = result.snapshotSha256();
        } catch (RuntimeException evidenceFailure) {
          exception.addSuppressed(evidenceFailure);
          snapshot = evidence.snapshotSha256();
        }
      } else {
        snapshot = memorySnapshot.finish();
      }
      try {
        writer.finishLayerUpdate(lease.failed(errors.first(), snapshot, errors.count()));
      } catch (RuntimeException ignored) {
        // Preserve the original failure; the adapter may be unavailable too.
      }
      throw exception;
    } finally {
      if (evidence != null) evidence.close();
    }
  }

  private static List<Integer> sortedOrders(Set<Integer> orders) {
    return orders.stream().sorted().toList();
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

    void add(String value) {
      count++;
      if (first == null || first.isBlank()) first = value == null || value.isBlank() ? "unknown scan error" : value;
    }

    int count() { return count; }
    String first() { return first == null ? "unknown scan error" : first; }
  }

  private static final class SnapshotHash {
    private final MessageDigest digest;

    SnapshotHash() {
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException("SHA-256 is unavailable", exception);
      }
    }

    void add(FileAsset file) {
      digest.update(file.fileId().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '\n');
    }

    String finish() {
      return java.util.HexFormat.of().formatHex(digest.digest());
    }
  }
}
