/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
