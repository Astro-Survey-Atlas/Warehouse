package org.zhejianglab.astro.atlas.core;

import java.util.Set;

public record ConeQuery(double ra, double dec, double radiusDeg, int limit, String cursor) implements SpatialQuery {
  public ConeQuery {
    Healpix.validateRa(ra);
    Healpix.validateDeclination(dec);
    if (!(radiusDeg > 0.0) || radiusDeg > 180.0) throw new IllegalArgumentException("radiusDeg must be > 0 and <= 180");
    QueryLimits.validate(limit, cursor);
  }

  @Override
  public Set<Long> order8Cells() {
    return Healpix.cellsForCone(ra, dec, radiusDeg);
  }
}
