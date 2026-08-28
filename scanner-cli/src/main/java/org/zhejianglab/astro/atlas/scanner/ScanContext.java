/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zhejianglab.astro.atlas.scanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoveragePrecision;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceContent;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

final class ScanContext {
  private final InputItem item;
  private final SourceContent content;
  private final ScanPlan plan;
  private final Map<String, SpatialCoverage> coverages = new LinkedHashMap<>();
  private final List<String> errors = new ArrayList<>();
  private int catalogRows;
  private int invalidCatalogRows;
  private int validCatalogRows;

  ScanContext(InputItem item, SourceContent content, ScanPlan plan) {
    this.item = item;
    this.content = content;
    this.plan = plan;
  }

  InputItem item() { return item; }
  SourceContent content() { return content; }
  ScanPlan plan() { return plan; }

  void addCoverage(int order, long cell, CoverageMethod method, CoveragePrecision precision, Integer sourceOrder) {
    Healpix.validateCell(order, cell);
    SpatialCoverage coverage = new SpatialCoverage(plan.layer().layerId(), item.fileId(), item.sourceUri(),
        order, cell, CoordinateFrame.ICRS, HealpixNesting.NESTED, method,
        plan.layer().coverageRole(), plan.layer().modality(), precision, sourceOrder);
    coverages.putIfAbsent(coverage.id(), coverage);
  }

  void addError(String message) {
    errors.add(message == null || message.isBlank() ? "unknown extraction error" : message);
  }

  void addCatalogRow() { catalogRows++; }
  void addInvalidCatalogRow() { invalidCatalogRows++; }
  void addValidCatalogRow() { validCatalogRows++; }

  ExtractionResult result() {
    return new ExtractionResult(List.copyOf(coverages.values()), errors,
        catalogRows, validCatalogRows, invalidCatalogRows);
  }
}
