package org.zhejianglab.astro.atlas.core;

public enum CoverageMethod {
  WCS("wcs"),
  CATALOG_COORDINATES("catalog_coordinates"),
  CATALOG_HEALPIX("catalog_healpix");

  private final String value;

  CoverageMethod(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
