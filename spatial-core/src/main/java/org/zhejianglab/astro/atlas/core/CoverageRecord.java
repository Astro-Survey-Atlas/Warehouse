package org.zhejianglab.astro.atlas.core;

public record CoverageRecord(SpatialCoverage value) implements MetadataRecord {
  public CoverageRecord {
    if (value == null) throw new IllegalArgumentException("coverage is required");
  }
}
