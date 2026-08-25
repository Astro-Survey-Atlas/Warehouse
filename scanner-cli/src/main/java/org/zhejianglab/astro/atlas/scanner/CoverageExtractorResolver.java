package org.zhejianglab.astro.atlas.scanner;

import org.zhejianglab.astro.atlas.core.ExtractionMode;
import org.zhejianglab.astro.atlas.core.ExtractionSpec;

public final class CoverageExtractorResolver {
  private CoverageExtractorResolver() {}

  public static CoverageExtractor resolve(ExtractionSpec extraction) {
    if (extraction == null || extraction.mode() == null) throw new IllegalArgumentException("extraction mode is required");
    return switch (extraction.mode()) {
      case FITS_WCS, FITS_HEADER_POSITION -> new FitsHeaderHandler();
      case CATALOG_RADEC, CATALOG_HEALPIX -> new CatalogHandler();
    };
  }
}
