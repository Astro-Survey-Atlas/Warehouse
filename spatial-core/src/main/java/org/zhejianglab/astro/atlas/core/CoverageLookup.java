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

import java.util.Collection;
import java.util.List;
import java.util.Set;

public record CoverageLookup(
    Set<String> layerIds,
    int order,
    Set<Long> pixels,
    int limit,
    String cursor) {
  public CoverageLookup {
    if (layerIds == null || layerIds.isEmpty()) throw new IllegalArgumentException("layerIds must not be empty");
    if (pixels == null || pixels.isEmpty()) throw new IllegalArgumentException("pixels must not be empty");
    layerIds = Set.copyOf(layerIds);
    if (layerIds.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("layerIds contain a blank value");
    Healpix.validateOrder(order);
    pixels.forEach(pixel -> Healpix.validateCell(order, pixel));
    pixels = Set.copyOf(pixels);
    QueryLimits.validate(limit, cursor);
  }

  public static CoverageLookup of(Collection<String> layers, int order, Collection<Long> pixels, int limit, String cursor) {
    return new CoverageLookup(Set.copyOf(layers), order, Set.copyOf(pixels), limit, cursor);
  }

  public List<String> sortedLayerIds() {
    return layerIds.stream().sorted().toList();
  }
}
