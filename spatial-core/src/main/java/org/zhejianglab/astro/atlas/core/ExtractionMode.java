package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ExtractionMode {
  FITS_WCS("fits-wcs"),
  FITS_HEADER_POSITION("fits-header-position"),
  CATALOG_RADEC("catalog-radec"),
  CATALOG_HEALPIX("catalog-healpix");

  private final String value;

  ExtractionMode(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static ExtractionMode fromJson(String value) {
    if (value == null) return null;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (ExtractionMode mode : values()) {
      if (mode.value.equals(normalized)) return mode;
    }
    throw new IllegalArgumentException("unsupported extraction mode: " + value);
  }
}
