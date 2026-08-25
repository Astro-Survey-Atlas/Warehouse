package org.zhejianglab.astro.atlas.scanner;

public interface Handler {
  void handle(ScanContext context) throws Exception;
}
