package org.zhejianglab.astro.atlas.core;

import java.util.List;

public record Page<T>(List<T> items, String nextCursor) {
  public Page {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
