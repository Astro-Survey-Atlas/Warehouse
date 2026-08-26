package org.zhejianglab.astro.atlas.core;

import java.util.Collection;
import java.time.Instant;

public interface IndexWriter {
  boolean tryBeginLayerUpdate(CoverageLayer updatingLayer);

  boolean renewLayerUpdate(String layerId, String scanRunId, Instant leaseExpiresAt);

  /** Persists a terminal state only while the caller still owns the UPDATING lease. */
  boolean finishLayerUpdate(CoverageLayer terminalLayer);

  void deleteCoverageForLayer(String layerId);

  void saveLayer(CoverageLayer layer);

  void upsertFileAsset(FileAsset fileAsset);

  void upsertCoverage(SpatialCoverage coverage);

  default void upsertBatch(Collection<FileAsset> fileAssets, Collection<SpatialCoverage> coverages) {
    if (fileAssets != null) fileAssets.forEach(this::upsertFileAsset);
    if (coverages != null) coverages.forEach(this::upsertCoverage);
  }
}
