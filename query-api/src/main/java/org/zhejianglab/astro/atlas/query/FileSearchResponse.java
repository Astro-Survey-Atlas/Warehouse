package org.zhejianglab.astro.atlas.query;

import java.util.List;

public record FileSearchResponse(List<FileSearchItem> items, int limit, String nextCursor, boolean truncated) {
  public FileSearchResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public FileSearchResponse(List<FileSearchItem> items, int limit, String nextCursor) {
    this(items, limit, nextCursor, nextCursor != null);
  }
}
