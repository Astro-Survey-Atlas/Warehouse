package org.zhejianglab.astro.atlas.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record FileAsset(
    String fileId,
    String sourceUri,
    String fileName,
    String parentUri,
    FileType fileType,
    Long sizeBytes,
    Instant lastModified,
    Instant indexedAt) {
  public FileAsset {
    sourceUri = SourceIdentity.canonicalize(sourceUri);
    if (!SourceIdentity.fileId(sourceUri).equals(fileId)) {
      throw new IllegalArgumentException("fileId must hash canonical sourceUri");
    }
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank");
    if (fileType == null) throw new IllegalArgumentException("fileType is required");
    if (sizeBytes != null && sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must not be negative");
    if (indexedAt == null) throw new IllegalArgumentException("indexedAt is required");
  }

  public static FileAsset from(InputItem item) {
    return new FileAsset(item.fileId(), item.sourceUri(), item.fileName(), item.parentUri(),
        item.fileType(), item.sizeBytes(), item.lastModified(), Instant.now());
  }

  public Map<String, Object> toDocument() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("file_id", fileId);
    document.put("source_uri", sourceUri);
    document.put("file_name", fileName);
    document.put("parent_uri", parentUri);
    document.put("file_type", fileType.name());
    document.put("size_bytes", sizeBytes);
    document.put("last_modified", lastModified);
    document.put("indexed_at", indexedAt);
    return Collections.unmodifiableMap(document);
  }
}
