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

package org.zhejianglab.astro.atlas.query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.zhejianglab.astro.atlas.core.CoverageLayer;
import org.zhejianglab.astro.atlas.core.CoverageLookup;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexContract;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.LayerState;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;
import org.zhejianglab.astro.atlas.core.SpatialQuery;

/** Read-only coverage-to-file join used by every HTTP search endpoint. */
public final class QueryService {
  private final IndexReader reader;

  public QueryService(IndexReader reader) {
    if (reader == null) throw new IllegalArgumentException("index reader is required");
    this.reader = reader;
  }

  public FileSearchResponse search(CoverageLookup lookup) {
    if (lookup == null) throw new IllegalArgumentException("coverage lookup is required");
    validateLayers(lookup);
    Map<String, List<MatchingCoverage>> matchingByFile = new LinkedHashMap<>();
    String cursor = lookup.cursor();
    String nextCursor = null;
    while (true) {
      CoverageLookup pageLookup = new CoverageLookup(lookup.layerIds(), lookup.order(), lookup.pixels(), lookup.limit(), cursor);
      Page<SpatialCoverage> coveragePage = reader.searchCoverage(pageLookup);
      for (SpatialCoverage coverage : coveragePage.items()) {
        matchingByFile.computeIfAbsent(coverage.sourceFileId(), ignored -> new java.util.ArrayList<>())
            .add(MatchingCoverage.from(coverage));
      }
      nextCursor = coveragePage.nextCursor();
      if (matchingByFile.size() >= lookup.limit() || nextCursor == null) break;
      if (nextCursor.equals(cursor)) throw new IllegalStateException("coverage cursor did not advance");
      cursor = nextCursor;
    }

    Collection<FileAsset> files = reader.findFiles(matchingByFile.keySet());
    Map<String, FileAsset> filesById = new LinkedHashMap<>();
    for (FileAsset file : files) filesById.put(file.fileId(), file);

    List<FileSearchItem> items = matchingByFile.entrySet().stream()
        .filter(entry -> filesById.containsKey(entry.getKey()))
        .map(entry -> FileSearchItem.from(filesById.get(entry.getKey()), entry.getValue()))
        .toList();
    return new FileSearchResponse(items, lookup.limit(), nextCursor, nextCursor != null);
  }

  /** Compatibility helper for point/cone diagnostics at the fixed diagnostic order. */
  public FileSearchResponse search(SpatialQuery query, Collection<String> layerIds) {
    if (query == null) throw new IllegalArgumentException("query is required");
    if (layerIds == null || layerIds.isEmpty()) throw new IllegalArgumentException("layerIds are required");
    if (query instanceof org.zhejianglab.astro.atlas.core.HealpixQuery healpix
        && healpix.order() != IndexContract.DIAGNOSTIC_ORDER) {
      throw new IllegalArgumentException("compatibility HEALPix diagnostics require order " + IndexContract.DIAGNOSTIC_ORDER);
    }
    return search(CoverageLookup.of(layerIds, IndexContract.DIAGNOSTIC_ORDER, query.order8Cells(), query.limit(), query.cursor()));
  }

  private void validateLayers(CoverageLookup lookup) {
    Collection<CoverageLayer> layers = reader.findLayers(lookup.layerIds());
    Map<String, CoverageLayer> byId = new java.util.HashMap<>();
    for (CoverageLayer layer : layers) byId.put(layer.layerId(), layer);
    for (String layerId : lookup.layerIds()) {
      CoverageLayer layer = byId.get(layerId);
      if (layer == null) throw new UnknownLayerException(layerId);
      if (layer.state() != LayerState.ACTIVE) throw new LayerStateException(layerId, layer.state());
      if (!layer.availableOrders().contains(lookup.order())) throw new LayerOrderException(layerId, lookup.order());
    }
  }

  public boolean isReady() {
    return reader.isReady();
  }
}
