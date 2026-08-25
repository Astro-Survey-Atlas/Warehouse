package org.zhejianglab.astro.atlas.scanner;

/** Coverage normalization is performed by ScanContext while records are appended. */
public final class CoverageHandler implements Handler {
  @Override
  public void handle(ScanContext context) {
    // Coverage records are already normalized and de-duplicated at insertion time.
  }
}
