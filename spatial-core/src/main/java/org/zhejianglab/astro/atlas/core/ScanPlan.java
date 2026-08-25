package org.zhejianglab.astro.atlas.core;

public record ScanPlan(
    Integer version,
    String scanRunId,
    LayerSpec layer,
    SourceSpec source,
    Filters filters,
    ExtractionSpec extraction,
    SinkSpec sink,
    EvidenceSpec evidence) {
  public ScanPlan {
    if (filters == null) filters = Filters.empty();
  }
}
