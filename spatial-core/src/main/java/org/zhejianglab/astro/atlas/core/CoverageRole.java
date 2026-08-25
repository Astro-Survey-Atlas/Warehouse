package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CoverageRole {
  FOOTPRINT("footprint"),
  OCCUPANCY("occupancy");

  private final String value;

  CoverageRole(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static CoverageRole fromJson(String value) {
    if (value == null) return null;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (CoverageRole role : values()) {
      if (role.value.equals(normalized)) return role;
    }
    throw new IllegalArgumentException("unsupported coverage role: " + value);
  }
}
