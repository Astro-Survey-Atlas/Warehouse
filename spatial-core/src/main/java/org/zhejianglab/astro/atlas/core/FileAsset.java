package org.zhejianglab.astro.atlas.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record FileAsset(
    String fileId,
    String sourceUri,
    String fileName,
    String parentUri,
    FileType fileType,
    Long sizeBytes,
    Instant lastModified,
    Modality modality,
    SpatialStatus spatialStatus,
    List<Long> coverageCells,
    Instant indexedAt) {
  public FileAsset {
    sourceUri = SourceIdentity.canonicalize(sourceUri);
    String expectedId = SourceIdentity.fileId(sourceUri);
    if (!expectedId.equals(fileId)) throw new IllegalArgumentException("fileId must hash canonical sourceUri");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank");
    if (fileType == null) throw new IllegalArgumentException("fileType is required");
    if (spatialStatus == null) throw new IllegalArgumentException("spatialStatus is required");
    if (indexedAt == null) throw new IllegalArgumentException("indexedAt is required");
    if (sizeBytes != null && sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must not be negative");
    List<Long> normalized = new ArrayList<>(coverageCells == null ? List.of() : coverageCells);
    normalized.forEach(cell -> {
      if (cell == null || cell < 0 || cell >= Healpix.INDEX_CELL_COUNT) throw new IllegalArgumentException("coverage cell is invalid");
    });
    normalized = normalized.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    coverageCells = List.copyOf(normalized);
  }

  public static FileAsset from(InputItem item, SpatialStatus status, List<Long> coverageCells, Modality modality) {
    return new FileAsset(item.fileId(), item.sourceUri(), item.fileName(), item.parentUri(), item.fileType(),
        item.sizeBytes(), item.lastModified(), modality, status, coverageCells, Instant.now());
  }

  public java.util.Map<String, Object> toDocument() {
    java.util.Map<String, Object> document = new java.util.LinkedHashMap<>();
    document.put("file_id", fileId);
    document.put("source_uri", sourceUri);
    document.put("file_name", fileName);
    document.put("parent_uri", parentUri);
    document.put("file_type", fileType.name());
    document.put("size_bytes", sizeBytes);
    document.put("last_modified", lastModified);
    document.put("modality", modality == null ? null : modality.value());
    document.put("spatial_status", spatialStatus.value());
    document.put("coverage_cells", coverageCells);
    document.put("indexed_at", indexedAt);
    return java.util.Collections.unmodifiableMap(document);
  }
}
