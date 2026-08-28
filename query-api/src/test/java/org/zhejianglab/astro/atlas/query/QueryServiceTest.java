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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zhejianglab.astro.atlas.core.CoverageLayer;
import org.zhejianglab.astro.atlas.core.CoverageLookup;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoveragePrecision;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.LayerSpec;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SourceIdentity;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

class QueryServiceTest {
  @Test
  void validatesActiveLayersAndJoinsCoverageHitsWithoutDuplicatingFiles() {
    String uri = "s3://survey/image.fits";
    FileAsset file = file(uri);
    long firstCell = Healpix.ang2pixNest(8, 180.25, -2.5);
    long secondCell = Healpix.ang2pixNest(8, 180.35, -2.5);
    SpatialCoverage first = coverage("image-layer", file, firstCell);
    SpatialCoverage second = coverage("image-layer", file, secondCell);
    CoverageLayer layer = activeLayer("image-layer", List.of(8));

    QueryService service = new QueryService(new IndexReader() {
      @Override public Collection<CoverageLayer> findLayers(Collection<String> ids) { return List.of(layer); }
      @Override public Page<SpatialCoverage> searchCoverage(CoverageLookup lookup) { return new Page<>(List.of(first, second), null); }
      @Override public Collection<FileAsset> findFiles(Collection<String> ids) { return List.of(file); }
    });

    FileSearchResponse response = service.search(CoverageLookup.of(List.of("image-layer"), 8,
        List.of(firstCell, secondCell), 10, null));
    assertEquals(1, response.items().size());
    assertEquals(file.fileId(), response.items().get(0).fileId());
    assertEquals(2, response.items().get(0).matchingCoverage().size());
    assertEquals("image-layer", response.items().get(0).matchingCoverage().get(0).layerId());
    assertEquals("estimated", response.items().get(0).matchingCoverage().get(0).precision());
  }

  @Test
  void fillsUniqueFileLimitAcrossCoveragePages() {
    FileAsset firstFile = file("s3://survey/first.fits");
    FileAsset secondFile = file("s3://survey/second.fits");
    long firstCell = Healpix.ang2pixNest(8, 180.25, -2.5);
    long secondCell = Healpix.ang2pixNest(8, 181.25, -2.5);
    SpatialCoverage firstCoverage = coverage("image-layer", firstFile, firstCell);
    SpatialCoverage secondCoverage = coverage("image-layer", secondFile, secondCell);
    CoverageLayer layer = activeLayer("image-layer", List.of(8));
    AtomicInteger calls = new AtomicInteger();

    QueryService service = new QueryService(new IndexReader() {
      @Override public Collection<CoverageLayer> findLayers(Collection<String> ids) { return List.of(layer); }
      @Override public Page<SpatialCoverage> searchCoverage(CoverageLookup lookup) {
        calls.incrementAndGet();
        return lookup.cursor() == null
            ? new Page<>(List.of(firstCoverage, firstCoverage), "next")
            : new Page<>(List.of(secondCoverage), null);
      }
      @Override public Collection<FileAsset> findFiles(Collection<String> ids) {
        return ids.contains(secondFile.fileId()) ? List.of(firstFile, secondFile) : List.of(firstFile);
      }
    });

    FileSearchResponse response = service.search(CoverageLookup.of(List.of("image-layer"), 8,
        List.of(firstCell, secondCell), 2, null));
    assertEquals(2, response.items().size());
    assertEquals(2, calls.get());
  }

  @Test
  void doesNotTurnUpdatingLayerIntoAnEmptyResult() {
    CoverageLayer updating = CoverageLayer.updating(layerSpec("image-layer"), "run", Instant.now().plusSeconds(60));
    QueryService service = new QueryService(new IndexReader() {
      @Override public Collection<CoverageLayer> findLayers(Collection<String> ids) { return List.of(updating); }
      @Override public Page<SpatialCoverage> searchCoverage(CoverageLookup lookup) { throw new AssertionError("must not search"); }
      @Override public Collection<FileAsset> findFiles(Collection<String> ids) { return List.of(); }
    });
    assertThrows(LayerStateException.class, () -> service.search(
        CoverageLookup.of(List.of("image-layer"), 8, List.of(0L), 10, null)));
  }

  private static FileAsset file(String uri) {
    return new FileAsset(SourceIdentity.fileId(uri), uri, uri.substring(uri.lastIndexOf('/') + 1), "s3://survey",
        FileType.FITS, 10L, null, Instant.now());
  }

  private static SpatialCoverage coverage(String layerId, FileAsset file, long cell) {
    return new SpatialCoverage(layerId, file.fileId(), file.sourceUri(), 8, cell, CoordinateFrame.ICRS,
        HealpixNesting.NESTED, CoverageMethod.FITS_WCS, CoverageRole.FOOTPRINT, Modality.IMAGE,
        CoveragePrecision.ESTIMATED, null);
  }

  private static CoverageLayer activeLayer(String layerId, List<Integer> orders) {
    return CoverageLayer.updating(layerSpec(layerId), "run", Instant.now().plusSeconds(60))
        .active("snapshot", orders, 1, 1, 0);
  }

  private static LayerSpec layerSpec(String layerId) {
    return new LayerSpec(layerId, "survey", "release", "product", Modality.IMAGE, CoverageRole.FOOTPRINT, null);
  }
}
