package org.zhejianglab.astro.atlas.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record SpatialCoverage(
    String sourceFileId,
    String sourceUri,
    int healpixOrder,
    long healpixCell,
    CoordinateFrame coordinateFrame,
    HealpixNesting nesting,
    CoverageMethod coverageMethod,
    CoverageRole coverageRole,
    Modality modality,
    String quality) {
  public SpatialCoverage {
    if (sourceFileId == null || sourceFileId.isBlank()) throw new IllegalArgumentException("sourceFileId must not be blank");
    sourceUri = SourceIdentity.canonicalize(sourceUri);
    if (!SourceIdentity.fileId(sourceUri).equals(sourceFileId)) {
      throw new IllegalArgumentException("sourceFileId must hash canonical sourceUri");
    }
    if (healpixOrder != IndexContract.ORDER) throw new IllegalArgumentException("coverage order must be 8");
    if (healpixCell < 0 || healpixCell >= Healpix.INDEX_CELL_COUNT) throw new IllegalArgumentException("coverage cell is invalid");
    if (coordinateFrame != CoordinateFrame.ICRS) throw new IllegalArgumentException("coverage frame must be ICRS");
    if (nesting != HealpixNesting.NESTED) throw new IllegalArgumentException("coverage nesting must be NESTED");
    if (coverageMethod == null || coverageRole == null) throw new IllegalArgumentException("coverage method and role are required");
  }

  public String id() {
    return SourceIdentity.coverageId(sourceFileId, healpixOrder, healpixCell, coverageRole);
  }

  public Map<String, Object> toDocument() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("source_file_id", sourceFileId);
    document.put("source_uri", sourceUri);
    document.put("healpix_order", healpixOrder);
    document.put("healpix_cell", healpixCell);
    document.put("coordinate_frame", coordinateFrame.name());
    document.put("nesting", nesting.name());
    document.put("coverage_method", coverageMethod.value());
    document.put("coverage_role", coverageRole.value());
    document.put("modality", modality == null ? null : modality.value());
    document.put("quality", quality);
    return java.util.Collections.unmodifiableMap(document);
  }
}
