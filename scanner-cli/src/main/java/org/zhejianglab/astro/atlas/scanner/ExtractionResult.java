package org.zhejianglab.astro.atlas.scanner;

import java.util.List;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

public record ExtractionResult(
    List<SpatialCoverage> coverages,
    List<String> errors,
    int catalogRows,
    int validCatalogRows,
    int invalidCatalogRows) {
  public ExtractionResult {
    coverages = coverages == null ? List.of() : List.copyOf(coverages);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
}
