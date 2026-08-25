package org.zhejianglab.astro.atlas.query;

import org.zhejianglab.astro.atlas.core.SpatialCoverage;

public record MatchingCoverage(
    String layerId,
    int order,
    long pixel,
    String method,
    String role,
    String precision,
    Integer sourceOrder) {
  public static MatchingCoverage from(SpatialCoverage coverage) {
    return new MatchingCoverage(coverage.layerId(), coverage.healpixOrder(), coverage.healpixCell(),
        coverage.coverageMethod().value(), coverage.coverageRole().value(), coverage.precision().value(),
        coverage.sourceOrder());
  }
}
