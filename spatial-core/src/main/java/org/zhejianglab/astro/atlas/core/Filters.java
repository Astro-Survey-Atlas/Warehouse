package org.zhejianglab.astro.atlas.core;

import java.util.List;

public record Filters(List<String> includeSuffixes, List<String> excludePatterns) {
  public Filters {
    includeSuffixes = includeSuffixes == null ? List.of() : List.copyOf(includeSuffixes);
    excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
  }

  public static Filters empty() {
    return new Filters(List.of(), List.of());
  }
}
