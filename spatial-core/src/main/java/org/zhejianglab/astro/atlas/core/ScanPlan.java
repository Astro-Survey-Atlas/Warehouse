package org.zhejianglab.astro.atlas.core;

import java.util.List;

public record ScanPlan(
    Integer version,
    SourceSpec source,
    Filters filters,
    List<String> handlers,
    Modality modality,
    CatalogSpec catalog,
    SinkSpec sink) {
  public ScanPlan {
    if (filters == null) filters = Filters.empty();
    if (handlers != null) handlers = List.copyOf(handlers);
    if (catalog == null) catalog = CatalogSpec.empty();
  }

  public ScanPlan(
      Integer version,
      SourceSpec source,
      Filters filters,
      List<String> handlers,
      Modality modality,
      SinkSpec sink) {
    this(version, source, filters, handlers, modality, CatalogSpec.empty(), sink);
  }
}
