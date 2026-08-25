package org.zhejianglab.astro.atlas.scanner;

import java.time.Instant;
import java.util.List;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.ScanPlanValidator;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

public final class ScanService {
  private static final int MAX_PENDING_RECORDS = 500;
  private final SourceAdapter source;
  private final IndexWriter writer;

  public ScanService(SourceAdapter source, IndexWriter writer) {
    this.source = source;
    this.writer = writer;
  }

  public ScanSummary scan(ScanPlan plan) {
    ScanPlanValidator.validate(plan);
    List<Handler> handlers = plan.handlers().stream().map(HandlerFactory::create).toList();
    List<org.zhejianglab.astro.atlas.core.InputItem> items = source.enumerate(plan);
    int coverageCount = 0;
    int processed = 0;
    int catalogRows = 0;
    int validCatalogRows = 0;
    int invalidCatalogRows = 0;
    int errorCount = 0;
    List<FileAsset> pendingFiles = new java.util.ArrayList<>();
    List<org.zhejianglab.astro.atlas.core.SpatialCoverage> pendingCoverages = new java.util.ArrayList<>();
    for (var item : items) {
      ScanContext context = new ScanContext(item, plan.modality(), source.open(item), plan);
      for (Handler handler : handlers) {
        try {
          handler.handle(context);
        } catch (Exception exception) {
          context.addError(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
      }
      SpatialStatus status = context.errors().isEmpty()
          ? (context.coverages().isEmpty() ? SpatialStatus.UNKNOWN : SpatialStatus.KNOWN)
          : SpatialStatus.ERROR;
      FileAsset fileAsset = FileAsset.from(item, status, context.coverages().stream().map(c -> c.healpixCell()).toList(), plan.modality());
      pendingFiles.add(fileAsset);
      pendingCoverages.addAll(context.coverages());
      coverageCount += context.coverages().size();
      catalogRows += context.catalogRows();
      validCatalogRows += context.validCatalogRows();
      invalidCatalogRows += context.invalidCatalogRows();
      errorCount += context.errors().size();
      processed++;
      if (pendingFiles.size() + pendingCoverages.size() >= MAX_PENDING_RECORDS) {
        writer.upsertBatch(pendingFiles, pendingCoverages);
        pendingFiles = new java.util.ArrayList<>();
        pendingCoverages = new java.util.ArrayList<>();
      }
    }
    if (!pendingFiles.isEmpty() || !pendingCoverages.isEmpty()) writer.upsertBatch(pendingFiles, pendingCoverages);
    return new ScanSummary("COMPLETED", items.size(), processed, coverageCount, catalogRows,
        validCatalogRows, invalidCatalogRows, errorCount, Instant.now());
  }
}
