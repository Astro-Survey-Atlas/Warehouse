package org.zhejianglab.astro.atlas.core;

import java.util.Collection;

public interface IndexReader {
  Page<SpatialCoverage> searchCoverage(Collection<Long> order8Cells, int limit, String cursor);

  Collection<FileAsset> findFiles(Collection<String> fileIds);

  default boolean isReady() {
    return true;
  }
}
