package org.zhejianglab.astro.atlas.core;

import java.util.Set;

public sealed interface SpatialQuery permits PointQuery, ConeQuery, HealpixQuery {
  int limit();
  String cursor();
  Set<Long> order8Cells();
}
