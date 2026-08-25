package org.zhejianglab.astro.atlas.query;

import java.util.List;

public record FileSearchResponse(List<FileSearchItem> items, int limit, String nextCursor) {
  public FileSearchResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
