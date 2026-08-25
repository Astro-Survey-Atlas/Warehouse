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
