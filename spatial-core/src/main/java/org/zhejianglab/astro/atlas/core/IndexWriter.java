package org.zhejianglab.astro.atlas.core;

import java.util.Collection;
/** Write-side contract shared by the scanner and its index adapters. */
public interface IndexWriter {
  void upsertFileAsset(FileAsset fileAsset);

  void upsertCoverage(SpatialCoverage coverage);

  /**
   * Writes one scanner batch. Adapters with a transport bulk API should override
   * this method; the default keeps small in-memory adapters source-compatible.
   */
  default void upsertBatch(Collection<FileAsset> fileAssets, Collection<SpatialCoverage> coverages) {
    if (fileAssets != null) fileAssets.forEach(this::upsertFileAsset);
    if (coverages != null) coverages.forEach(this::upsertCoverage);
  }
}
