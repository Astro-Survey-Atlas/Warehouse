package org.zhejianglab.astro.atlas.core;

public record ExtractionSpec(ExtractionMode mode, Integer outputOrder, CatalogSpec catalog) {
  public ExtractionSpec {
    if (catalog == null) catalog = CatalogSpec.empty();
  }
}
