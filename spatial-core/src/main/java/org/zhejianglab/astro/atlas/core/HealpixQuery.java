package org.zhejianglab.astro.atlas.core;

import java.util.Set;

public record HealpixQuery(int order, long pixel, int limit, String cursor) implements SpatialQuery {
  public HealpixQuery {
    QueryLimits.validate(limit, cursor);
    Healpix.normalizeQueryCells(order, pixel);
  }

  @Override
  public Set<Long> order8Cells() {
    return Healpix.normalizeQueryCells(order, pixel);
  }
}
