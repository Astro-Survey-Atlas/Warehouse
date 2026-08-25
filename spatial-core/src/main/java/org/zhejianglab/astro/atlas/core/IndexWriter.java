package org.zhejianglab.astro.atlas.core;

/** Write-side contract shared by the scanner and its index adapters. */
public interface IndexWriter {
  void upsertFileAsset(FileAsset fileAsset);

  void upsertCoverage(SpatialCoverage coverage);
}
