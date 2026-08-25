package org.zhejianglab.astro.atlas.scanner;

import java.time.Instant;
import java.util.List;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.ScanPlanValidator;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

public final class ScanService {
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
    for (var item : items) {
      ScanContext context = new ScanContext(item, plan.modality(), source.open(item));
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
      writer.upsertFileAsset(fileAsset);
      context.coverages().forEach(writer::upsertCoverage);
      coverageCount += context.coverages().size();
      processed++;
    }
    return new ScanSummary("COMPLETED", items.size(), processed, coverageCount, Instant.now());
  }
}
