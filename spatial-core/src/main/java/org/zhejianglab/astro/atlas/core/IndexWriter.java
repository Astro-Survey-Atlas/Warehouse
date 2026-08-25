package org.zhejianglab.astro.atlas.core;

import java.util.Collection;

public interface IndexWriter {
  boolean tryBeginLayerUpdate(CoverageLayer updatingLayer);

  void deleteCoverageForLayer(String layerId);

  void saveLayer(CoverageLayer layer);

  void upsertFileAsset(FileAsset fileAsset);

  void upsertCoverage(SpatialCoverage coverage);

  default void upsertBatch(Collection<FileAsset> fileAssets, Collection<SpatialCoverage> coverages) {
    if (fileAssets != null) fileAssets.forEach(this::upsertFileAsset);
    if (coverages != null) coverages.forEach(this::upsertCoverage);
  }
}
