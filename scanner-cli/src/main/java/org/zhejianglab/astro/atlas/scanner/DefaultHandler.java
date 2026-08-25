package org.zhejianglab.astro.atlas.scanner;

/** The final FileAsset is assembled by ScanService after all handlers complete. */
public final class DefaultHandler implements Handler {
  @Override
  public void handle(ScanContext context) {
    // The default handler reserves the pipeline stage for basic file metadata.
  }
}
