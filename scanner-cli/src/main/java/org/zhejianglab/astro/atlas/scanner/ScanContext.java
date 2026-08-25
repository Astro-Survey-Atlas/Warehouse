package org.zhejianglab.astro.atlas.scanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.SourceContent;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

public final class ScanContext {
  private final InputItem item;
  private final Modality modality;
  private final SourceContent content;
  private final Map<String, SpatialCoverage> coverages = new LinkedHashMap<>();
  private final List<String> errors = new ArrayList<>();

  public ScanContext(InputItem item, Modality modality, SourceContent content) {
    this.item = item;
    this.modality = modality;
    this.content = content;
  }

  public InputItem item() {
    return item;
  }

  public Modality modality() {
    return modality;
  }

  public SourceContent content() {
    return content;
  }

  public void addCoverage(long cell, CoverageMethod method) {
    addCoverage(cell, method, CoverageRole.OCCUPANCY, null);
  }

  public void addCoverage(long cell, CoverageMethod method, CoverageRole role, String quality) {
    if (cell < 0 || cell >= Healpix.INDEX_CELL_COUNT) throw new IllegalArgumentException("coverage cell is invalid");
    SpatialCoverage coverage = new SpatialCoverage(item.fileId(), item.sourceUri(), 8, cell,
        org.zhejianglab.astro.atlas.core.CoordinateFrame.ICRS,
        org.zhejianglab.astro.atlas.core.HealpixNesting.NESTED, method, role, modality, quality);
    coverages.putIfAbsent(coverage.id(), coverage);
  }

  public List<SpatialCoverage> coverages() {
    return List.copyOf(coverages.values());
  }

  public void addError(String message) {
    errors.add(message);
  }

  public List<String> errors() {
    return List.copyOf(errors);
  }
}
