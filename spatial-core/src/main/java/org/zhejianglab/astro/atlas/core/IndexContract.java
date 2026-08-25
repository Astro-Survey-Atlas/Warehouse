package org.zhejianglab.astro.atlas.core;

public final class IndexContract {
  public static final String LAYER_INDEX = "ast_layer_index_v1";
  public static final String FILE_INDEX = "ast_file_index_v1";
  public static final String COVERAGE_INDEX = "ast_coverage_index_v1";
  public static final int DIAGNOSTIC_ORDER = 8;
  public static final int ORDER = DIAGNOSTIC_ORDER;
  public static final CoordinateFrame FRAME = CoordinateFrame.ICRS;
  public static final HealpixNesting NESTING = HealpixNesting.NESTED;

  private IndexContract() {}
}
