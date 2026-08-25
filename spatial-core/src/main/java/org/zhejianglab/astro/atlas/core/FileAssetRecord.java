package org.zhejianglab.astro.atlas.core;

public record FileAssetRecord(FileAsset value) implements MetadataRecord {
  public FileAssetRecord {
    if (value == null) throw new IllegalArgumentException("file asset is required");
  }
}
