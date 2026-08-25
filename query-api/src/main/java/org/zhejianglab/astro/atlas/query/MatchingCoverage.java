package org.zhejianglab.astro.atlas.query;

import org.zhejianglab.astro.atlas.core.SpatialCoverage;

public record MatchingCoverage(int order, long pixel, String method) {
  public static MatchingCoverage from(SpatialCoverage coverage) {
    return new MatchingCoverage(coverage.healpixOrder(), coverage.healpixCell(), coverage.coverageMethod().value());
  }
}
