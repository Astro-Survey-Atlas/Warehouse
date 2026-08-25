package org.zhejianglab.astro.atlas.core;

public enum CoverageRole {
  FOOTPRINT("footprint"),
  OCCUPANCY("occupancy");

  private final String value;

  CoverageRole(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
