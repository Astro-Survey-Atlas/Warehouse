package org.zhejianglab.astro.atlas.core;

import java.util.Collection;

public interface IndexReader {
  Collection<CoverageLayer> findLayers(Collection<String> layerIds);

  Page<SpatialCoverage> searchCoverage(CoverageLookup lookup);

  Collection<FileAsset> findFiles(Collection<String> fileIds);

  default boolean isReady() {
    return true;
  }
}
