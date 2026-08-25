package org.zhejianglab.astro.atlas.scanner;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.zhejianglab.astro.atlas.core.CoverageLayer;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.ScanPlanValidator;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

public final class ScanService {
  private static final int MAX_PENDING_RECORDS = 500;
  private final SourceAdapter source;
  private final IndexWriter writer;
  private final EvidenceWriter evidenceWriter = new EvidenceWriter();

  public ScanService(SourceAdapter source, IndexWriter writer) {
    this.source = source;
    this.writer = writer;
  }

  public ScanSummary scan(ScanPlan plan) {
    return scan(plan, false);
  }

  public ScanSummary scan(ScanPlan plan, boolean memoryMode) {
    ScanPlanValidator.validate(plan, memoryMode);
    CoverageLayer updating = CoverageLayer.updating(plan.layer(), plan.scanRunId(), Instant.now().plusSeconds(3600));
    if (!writer.tryBeginLayerUpdate(updating)) {
      throw new IllegalStateException("layer update is already in progress: " + plan.layer().layerId());
    }
    writer.saveLayer(updating);
    List<FileAsset> allFiles = new ArrayList<>();
    List<SpatialCoverage> allCoverages = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    int processed = 0;
    int catalogRows = 0;
    int validCatalogRows = 0;
    int invalidCatalogRows = 0;
    try {
      List<org.zhejianglab.astro.atlas.core.InputItem> items = source.enumerate(plan);
      writer.deleteCoverageForLayer(plan.layer().layerId());
      CoverageExtractor extractor = CoverageExtractorResolver.resolve(plan.extraction());
      List<FileAsset> pendingFiles = new ArrayList<>();
      List<SpatialCoverage> pendingCoverages = new ArrayList<>();
      for (var item : items) {
        FileAsset fileAsset = FileAsset.from(item);
        ExtractionResult result;
        try {
          result = extractor.extract(item, source.open(item), plan);
        } catch (RuntimeException exception) {
          result = new ExtractionResult(List.of(), List.of(message(exception)), 0, 0, 0);
        }
        pendingFiles.add(fileAsset);
        pendingCoverages.addAll(result.coverages());
        allFiles.add(fileAsset);
        allCoverages.addAll(result.coverages());
        errors.addAll(result.errors());
        catalogRows += result.catalogRows();
        validCatalogRows += result.validCatalogRows();
        invalidCatalogRows += result.invalidCatalogRows();
        processed++;
        if (pendingFiles.size() + pendingCoverages.size() >= MAX_PENDING_RECORDS) {
          writer.upsertBatch(pendingFiles, pendingCoverages);
          pendingFiles = new ArrayList<>();
          pendingCoverages = new ArrayList<>();
        }
      }
      if (!pendingFiles.isEmpty() || !pendingCoverages.isEmpty()) writer.upsertBatch(pendingFiles, pendingCoverages);
      EvidenceWriter.EvidenceResult evidence = memoryMode
          ? new EvidenceWriter.EvidenceResult(snapshotHash(allFiles), null)
          : evidenceWriter.write(Path.of(plan.evidence().outputPath()), plan.scanRunId(), plan.layer().layerId(), allFiles, allCoverages, errors);
      List<Integer> orders = allCoverages.stream().map(SpatialCoverage::healpixOrder).distinct().sorted().toList();
      CoverageLayer active = updating.active(evidence.snapshotSha256(), orders, allFiles.size(), allCoverages.size(), errors.size());
      writer.saveLayer(active);
      return new ScanSummary("COMPLETED", plan.scanRunId(), plan.layer().layerId(), evidence.snapshotSha256(),
          items.size(), processed, allCoverages.size(), catalogRows, validCatalogRows, invalidCatalogRows,
          errors.size(), orders, evidence.path(), Instant.now());
    } catch (RuntimeException exception) {
      String failure = message(exception);
      errors.add(failure);
      String snapshot = snapshotHash(allFiles);
      try {
        writer.saveLayer(updating.failed(failure, snapshot, errors.size()));
      } catch (RuntimeException ignored) {
        // Preserve the original failure; the adapter may be unavailable too.
      }
      throw exception;
    }
  }

  private static String snapshotHash(List<FileAsset> files) {
    String canonical = files.stream().map(FileAsset::fileId).sorted().reduce("", (left, right) -> left + right + "\n");
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String message(Throwable exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }
}
