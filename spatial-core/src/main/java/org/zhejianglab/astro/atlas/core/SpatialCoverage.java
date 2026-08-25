package org.zhejianglab.astro.atlas.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SpatialCoverage(
    String layerId,
    String sourceFileId,
    String sourceUri,
    int healpixOrder,
    long healpixCell,
    CoordinateFrame coordinateFrame,
    HealpixNesting nesting,
    CoverageMethod coverageMethod,
    CoverageRole coverageRole,
    Modality modality,
    CoveragePrecision precision,
    Integer sourceOrder) {
  public SpatialCoverage {
    if (layerId == null || layerId.isBlank()) throw new IllegalArgumentException("layerId must not be blank");
    if (sourceFileId == null || sourceFileId.isBlank()) throw new IllegalArgumentException("sourceFileId must not be blank");
    sourceUri = SourceIdentity.canonicalize(sourceUri);
    if (!SourceIdentity.fileId(sourceUri).equals(sourceFileId)) {
      throw new IllegalArgumentException("sourceFileId must hash canonical sourceUri");
    }
    Healpix.validateCell(healpixOrder, healpixCell);
    if (coordinateFrame != CoordinateFrame.ICRS) throw new IllegalArgumentException("coverage frame must be ICRS");
    if (nesting != HealpixNesting.NESTED) throw new IllegalArgumentException("coverage nesting must be NESTED");
    if (coverageMethod == null || coverageRole == null || modality == null || precision == null) {
      throw new IllegalArgumentException("coverage method, role, modality, and precision are required");
    }
    if (sourceOrder != null) Healpix.validateOrder(sourceOrder);
  }

  public String id() {
    return SourceIdentity.coverageId(layerId, sourceFileId, healpixOrder, healpixCell, coverageRole);
  }

  public Map<String, Object> toDocument() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("layer_id", layerId);
    document.put("source_file_id", sourceFileId);
    document.put("source_uri", sourceUri);
    document.put("healpix_order", healpixOrder);
    document.put("healpix_cell", healpixCell);
    document.put("coordinate_frame", coordinateFrame.name());
    document.put("nesting", nesting.name());
    document.put("coverage_method", coverageMethod.value());
    document.put("coverage_role", coverageRole.value());
    document.put("modality", modality.value());
    document.put("precision", precision.value());
    document.put("source_order", sourceOrder);
    return Collections.unmodifiableMap(document);
  }
}
