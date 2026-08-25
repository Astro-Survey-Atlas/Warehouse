package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CoveragePrecision {
  EXACT("exact"),
  ESTIMATED("estimated"),
  ENTRYPOINT_ONLY("entrypoint-only");

  private final String value;

  CoveragePrecision(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static CoveragePrecision fromJson(String value) {
    if (value == null) return null;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (CoveragePrecision precision : values()) {
      if (precision.value.equals(normalized)) return precision;
    }
    throw new IllegalArgumentException("unsupported coverage precision: " + value);
  }
}
