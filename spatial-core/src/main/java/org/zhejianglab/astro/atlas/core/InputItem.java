package org.zhejianglab.astro.atlas.core;

import java.time.Instant;

public record InputItem(
    String sourceUri,
    String fileName,
    String parentUri,
    FileType fileType,
    Long sizeBytes,
    Instant lastModified) {
  public InputItem {
    sourceUri = SourceIdentity.canonicalize(sourceUri);
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank");
    if (fileType == null) fileType = FileType.fromFileName(fileName);
    if (sizeBytes != null && sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must not be negative");
  }

  public String fileId() {
    return SourceIdentity.fileId(sourceUri);
  }
}
