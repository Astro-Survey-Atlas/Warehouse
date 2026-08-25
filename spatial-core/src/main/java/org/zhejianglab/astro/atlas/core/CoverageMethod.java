package org.zhejianglab.astro.atlas.core;

public enum CoverageMethod {
  FITS_WCS("fits_wcs"),
  FITS_HEADER_POSITION("fits_header_position"),
  CATALOG_RADEC("catalog_radec"),
  CATALOG_HEALPIX("catalog_healpix");

  private final String value;

  CoverageMethod(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static CoverageMethod fromValue(String value) {
    for (CoverageMethod method : values()) {
      if (method.value.equals(value)) return method;
    }
    throw new IllegalArgumentException("unsupported coverage method: " + value);
  }
}
