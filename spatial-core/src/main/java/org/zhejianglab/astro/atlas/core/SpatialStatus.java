package org.zhejianglab.astro.atlas.core;

public enum SpatialStatus {
  KNOWN("known"),
  UNKNOWN("unknown"),
  ERROR("error");

  private final String value;

  SpatialStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
