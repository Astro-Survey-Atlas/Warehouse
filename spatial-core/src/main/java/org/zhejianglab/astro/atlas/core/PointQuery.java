package org.zhejianglab.astro.atlas.core;

import java.util.Set;

public record PointQuery(double ra, double dec, int limit, String cursor) implements SpatialQuery {
  public PointQuery {
    Healpix.validateRa(ra);
    Healpix.validateDeclination(dec);
    QueryLimits.validate(limit, cursor);
  }

  @Override
  public Set<Long> order8Cells() {
    return Set.of(Healpix.ang2pixNest(IndexContract.ORDER, ra, dec));
  }
}
