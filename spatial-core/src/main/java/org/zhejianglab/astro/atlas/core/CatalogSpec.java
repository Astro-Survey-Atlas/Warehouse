package org.zhejianglab.astro.atlas.core;

/** Optional column overrides for CSV and TSV spatial extraction. */
public record CatalogSpec(
    String raColumn,
    String decColumn,
    String healpixColumn,
    String healpixOrderColumn,
    Integer healpixOrder) {
  public CatalogSpec {
    raColumn = normalize(raColumn);
    decColumn = normalize(decColumn);
    healpixColumn = normalize(healpixColumn);
    healpixOrderColumn = normalize(healpixOrderColumn);
  }

  public static CatalogSpec empty() {
    return new CatalogSpec(null, null, null, null, null);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
