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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.zhejianglab.astro.atlas.core.CoverageLayer;
import org.zhejianglab.astro.atlas.core.CoverageLookup;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.LayerState;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

/** Deterministic fake index used by local contract tests and development. */
public final class InMemoryIndex implements IndexWriter, IndexReader {
  private final Map<String, FileAsset> files = new ConcurrentHashMap<>();
  private final Map<String, SpatialCoverage> coverages = new ConcurrentHashMap<>();
  private final Map<String, CoverageLayer> layers = new ConcurrentHashMap<>();

  @Override
  public synchronized boolean tryBeginLayerUpdate(CoverageLayer updatingLayer) {
    CoverageLayer current = layers.get(updatingLayer.layerId());
    if (current != null && current.state() == LayerState.UPDATING
        && current.leaseExpiresAt() != null && current.leaseExpiresAt().isAfter(Instant.now())) return false;
    layers.put(updatingLayer.layerId(), updatingLayer);
    return true;
  }

  @Override
  public synchronized boolean renewLayerUpdate(String layerId, String scanRunId, Instant leaseExpiresAt) {
    CoverageLayer current = layers.get(layerId);
    if (current == null || current.state() != LayerState.UPDATING
        || !current.scanRunId().equals(scanRunId)
        || current.leaseExpiresAt() == null || !current.leaseExpiresAt().isAfter(Instant.now())) return false;
    layers.put(layerId, current.renewed(leaseExpiresAt));
    return true;
  }

  @Override
  public synchronized boolean finishLayerUpdate(CoverageLayer terminalLayer) {
    CoverageLayer current = layers.get(terminalLayer.layerId());
    if (current == null || current.state() != LayerState.UPDATING
        || !current.scanRunId().equals(terminalLayer.scanRunId())) return false;
    if (current.leaseExpiresAt() == null || !current.leaseExpiresAt().isAfter(Instant.now())) return false;
    layers.put(terminalLayer.layerId(), terminalLayer);
    return true;
  }

  @Override
  public void deleteCoverageForLayer(String layerId) {
    coverages.entrySet().removeIf(entry -> entry.getValue().layerId().equals(layerId));
  }

  @Override
  public void saveLayer(CoverageLayer layer) {
    layers.put(layer.layerId(), layer);
  }

  @Override
  public void upsertFileAsset(FileAsset fileAsset) {
    files.put(fileAsset.fileId(), fileAsset);
  }

  @Override
  public void upsertCoverage(SpatialCoverage coverage) {
    coverages.put(coverage.id(), coverage);
  }

  @Override
  public Collection<CoverageLayer> findLayers(Collection<String> layerIds) {
    return layerIds == null ? List.of() : layerIds.stream().map(layers::get).filter(java.util.Objects::nonNull).toList();
  }

  @Override
  public Page<SpatialCoverage> searchCoverage(CoverageLookup lookup) {
    List<SpatialCoverage> matching = coverages.values().stream()
        .filter(coverage -> lookup.layerIds().contains(coverage.layerId()))
        .filter(coverage -> lookup.order() == coverage.healpixOrder())
        .filter(coverage -> lookup.pixels().contains(coverage.healpixCell()))
        .filter(coverage -> {
          CoverageLayer layer = layers.get(coverage.layerId());
          return layer != null && layer.state() == LayerState.ACTIVE;
        })
        .sorted(Comparator.comparing(SpatialCoverage::id))
        .toList();
    int offset = parseCursor(lookup.cursor());
    if (offset > matching.size()) throw new IllegalArgumentException("cursor is outside result set");
    int end = Math.min(matching.size(), offset + lookup.limit());
    String next = end < matching.size() ? Integer.toString(end) : null;
    return new Page<>(matching.subList(offset, end), next);
  }

  @Override
  public Collection<FileAsset> findFiles(Collection<String> fileIds) {
    List<FileAsset> result = new ArrayList<>();
    if (fileIds == null) return result;
    for (String fileId : fileIds) {
      FileAsset file = files.get(fileId);
      if (file != null) result.add(file);
    }
    return result;
  }

  public List<FileAsset> files() {
    return files.values().stream().sorted(Comparator.comparing(FileAsset::fileId)).toList();
  }

  public List<SpatialCoverage> coverages() {
    return coverages.values().stream().sorted(Comparator.comparing(SpatialCoverage::id)).toList();
  }

  public List<CoverageLayer> layers() {
    return layers.values().stream().sorted(Comparator.comparing(CoverageLayer::layerId)).toList();
  }

  private static int parseCursor(String cursor) {
    if (cursor == null) return 0;
    try {
      return Integer.parseInt(cursor);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("malformed cursor");
    }
  }
}
