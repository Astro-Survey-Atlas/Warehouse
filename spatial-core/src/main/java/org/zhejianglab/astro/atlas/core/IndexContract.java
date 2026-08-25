package org.zhejianglab.astro.atlas.core;

/** Stable names and coordinate settings shared by writers and readers. */
public final class IndexContract {
  public static final String FILE_INDEX = "ast_file_index_v1";
  public static final String COVERAGE_INDEX = "ast_coverage_index_v1";
  public static final int ORDER = 8;
  public static final CoordinateFrame FRAME = CoordinateFrame.ICRS;
  public static final HealpixNesting NESTING = HealpixNesting.NESTED;

  private IndexContract() {}
}
