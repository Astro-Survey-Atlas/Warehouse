package org.zhejianglab.astro.atlas.core;

public final class QueryLimits {
  public static final int DEFAULT = 100;
  public static final int MAXIMUM = 1000;

  private QueryLimits() {}

  public static void validate(int limit, String cursor) {
    if (limit < 1 || limit > MAXIMUM) throw new IllegalArgumentException("limit must be between 1 and 1000");
    if (cursor != null && cursor.isBlank()) throw new IllegalArgumentException("cursor must not be blank");
  }
}
