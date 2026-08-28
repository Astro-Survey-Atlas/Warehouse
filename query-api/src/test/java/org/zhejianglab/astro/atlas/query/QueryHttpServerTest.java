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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

class QueryHttpServerTest {
  @Test
  void servesHealthReadinessAndV2Lookup() throws Exception {
    String sourceUri = "s3://survey/image.fits";
    FileAsset file = file(sourceUri);
    long cell = Healpix.ang2pixNest(8, 180.25, -2.5);
    SpatialCoverage coverage = coverage(file, cell);
    CoverageLayer layer = CoverageLayer.updating(layerSpec(), "run", Instant.now().plusSeconds(60))
        .active("snapshot", List.of(8), 1, 1, 0);

    IndexReader reader = new IndexReader() {
      @Override public Collection<CoverageLayer> findLayers(Collection<String> ids) { return List.of(layer); }
      @Override public Page<SpatialCoverage> searchCoverage(CoverageLookup lookup) { return new Page<>(List.of(coverage), null); }
      @Override public Collection<FileAsset> findFiles(Collection<String> ids) { return List.of(file); }
    };

    try (QueryHttpServer server = new QueryHttpServer("127.0.0.1", 0, new QueryService(reader))) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> health = get(client, server, "/healthz");
      assertEquals(200, health.statusCode());
      assertTrue(health.body().contains("\"status\":\"ok\""));
      assertEquals(200, get(client, server, "/readyz").statusCode());

      HttpResponse<String> search = get(client, server,
          "/v2/files/healpix?layers=image-layer&order=8&pixels=" + cell);
      assertEquals(200, search.statusCode());
      assertTrue(search.body().contains(file.fileId()));
      assertTrue(search.body().contains("\"layerId\":\"image-layer\""));
      assertTrue(search.body().contains("\"precision\":\"estimated\""));

      HttpResponse<String> invalid = get(client, server, "/v2/files/healpix?layers=image-layer&order=8");
      assertEquals(400, invalid.statusCode());
      assertTrue(invalid.body().contains("\"code\":\"INVALID_QUERY\""));
    }
  }

  private static HttpResponse<String> get(HttpClient client, QueryHttpServer server, String path) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static FileAsset file(String uri) {
    return new FileAsset(SourceIdentity.fileId(uri), uri, "image.fits", "s3://survey", FileType.FITS,
        10L, null, Instant.now());
  }

  private static SpatialCoverage coverage(FileAsset file, long cell) {
    return new SpatialCoverage("image-layer", file.fileId(), file.sourceUri(), 8, cell, CoordinateFrame.ICRS,
        HealpixNesting.NESTED, CoverageMethod.FITS_WCS, CoverageRole.FOOTPRINT, Modality.IMAGE,
        CoveragePrecision.ESTIMATED, null);
  }

  private static LayerSpec layerSpec() {
    return new LayerSpec("image-layer", "survey", "release", "product", Modality.IMAGE, CoverageRole.FOOTPRINT, null);
  }
}
