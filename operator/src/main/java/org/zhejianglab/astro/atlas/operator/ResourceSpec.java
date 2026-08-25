package org.zhejianglab.astro.atlas.operator;

import java.util.Map;
import java.util.TreeMap;

public record ResourceSpec(Map<String, String> requests, Map<String, String> limits) {
  public ResourceSpec {
    requests = normalize(requests);
    limits = normalize(limits);
  }

  public static ResourceSpec empty() {
    return new ResourceSpec(Map.of(), Map.of());
  }

  private static Map<String, String> normalize(Map<String, String> values) {
    return values == null ? Map.of() : Map.copyOf(new TreeMap<>(values));
  }
}
