package org.zhejianglab.astro.atlas.core;

import java.io.IOException;
import java.io.InputStream;

/** Opens the content of a source item without exposing transport details. */
@FunctionalInterface
public interface SourceContent {
  InputStream open() throws IOException;
}
