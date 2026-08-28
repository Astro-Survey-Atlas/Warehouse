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

package org.zhejianglab.astro.atlas.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import org.zhejianglab.astro.atlas.core.IndexContract;
import org.zhejianglab.astro.atlas.core.LayerSpec;
import org.zhejianglab.astro.atlas.core.LayerState;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.SourceIdentity;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

class ElasticsearchAdapterTest {
  @Test
  void writesThreeCurrentStateDocumentsAndReadsMultiOrderCoverage() throws Exception {
    String sourceUri = "s3://survey/image.fits";
    FileAsset file = file(sourceUri);
    long cell = Healpix.ang2pixNest(4, 180.25, -2.5);
    CoverageLayer layer = activeLayer("image-layer", List.of(4, 8));
    SpatialCoverage coverage = coverage(layer.layerId(), file, 4, cell);
    AtomicReference<String> bulk = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> handle(exchange, layer, file, coverage, bulk));
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      adapter.saveLayer(layer);
      assertTrue(bulk.get().contains(IndexContract.LAYER_INDEX));
      adapter.upsertFileAsset(file);
      assertTrue(bulk.get().contains(IndexContract.FILE_INDEX));
      adapter.upsertCoverage(coverage);
      assertTrue(bulk.get().contains(IndexContract.COVERAGE_INDEX));

      var foundLayers = adapter.findLayers(List.of(layer.layerId()));
      assertEquals(1, foundLayers.size());
      var page = adapter.searchCoverage(CoverageLookup.of(List.of(layer.layerId()), 4, List.of(cell), 1, null));
      assertEquals(1, page.items().size());
      assertEquals(4, page.items().get(0).healpixOrder());
      assertNotNull(page.nextCursor());
      assertThrows(IllegalArgumentException.class, () -> adapter.searchCoverage(
          CoverageLookup.of(List.of(layer.layerId()), 4, List.of(cell + 1), 1, page.nextCursor())));
      assertEquals(1, adapter.findFiles(List.of(file.fileId())).size());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void deletesOnlyCoverageForTheRequestedLayer() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestURI().getPath());
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      respond(exchange, "{\"deleted\":1}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      adapter.deleteCoverageForLayer("layer-a");
      assertTrue(body.get().contains("layer_id"));
      assertTrue(body.get().contains("layer-a"));
      assertEquals("/" + IndexContract.COVERAGE_INDEX + "/_delete_by_query", path.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsUnexpiredLeaseAndAcceptsExpiredLease() throws Exception {
    String runId = "existing-run";
    AtomicReference<String> lease = new AtomicReference<>("2099-01-01T00:00:00Z");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String source = "{\"layer_id\":\"image-layer\",\"state\":\"UPDATING\",\"scan_run_id\":\""
          + runId + "\",\"lease_expires_at\":\"" + lease.get() + "\"}";
      if (exchange.getRequestMethod().equals("PUT")) {
        respond(exchange, "{\"result\":\"updated\"}");
        return;
      }
      respond(exchange, "{\"found\":true,\"_seq_no\":1,\"_primary_term\":1,\"_source\":" + source + "}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      CoverageLayer candidate = CoverageLayer.updating(layerSpec("image-layer"), "new-run", Instant.now().plusSeconds(60));
      assertTrue(!adapter.tryBeginLayerUpdate(candidate));
      assertTrue(!adapter.tryBeginLayerUpdate(
          CoverageLayer.updating(layerSpec("image-layer"), runId, Instant.now().plusSeconds(60))));
      lease.set("2000-01-01T00:00:00Z");
      assertTrue(adapter.tryBeginLayerUpdate(candidate));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void renewsBeforeExpiryAndRefusesTerminalWriteAfterExpiry() throws Exception {
    AtomicReference<String> lease = new AtomicReference<>("2099-01-01T00:00:00Z");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String source = layerJson(CoverageLayer.updating(layerSpec("image-layer"), "run-1",
          Instant.parse(lease.get())));
      if (exchange.getRequestMethod().equals("PUT")) {
        respond(exchange, "{\"result\":\"updated\"}");
        return;
      }
      respond(exchange, "{\"found\":true,\"_seq_no\":1,\"_primary_term\":1,\"_source\":" + source + "}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      assertTrue(adapter.renewLayerUpdate("image-layer", "run-1", Instant.now().plusSeconds(120)));
      CoverageLayer terminal = CoverageLayer.updating(layerSpec("image-layer"), "run-1",
          Instant.now().plusSeconds(60)).active("snapshot", List.of(8), 1, 1, 0);
      assertTrue(adapter.finishLayerUpdate(terminal));
      lease.set("2000-01-01T00:00:00Z");
      assertTrue(!adapter.finishLayerUpdate(terminal));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void retriesOnlyRetryableBulkItems() throws Exception {
    AtomicReference<Integer> calls = new AtomicReference<>(0);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      int call = calls.updateAndGet(value -> value + 1);
      respond(exchange, call == 1
          ? "{\"errors\":true,\"items\":[{\"index\":{\"status\":429}}]}"
          : "{\"errors\":false,\"items\":[{\"index\":{\"status\":201}}]}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      adapter.upsertFileAsset(file("s3://survey/retry.fits"));
      assertEquals(2, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void splitsBulkRequestsAtTheRecordLimit() throws Exception {
    AtomicReference<Integer> requests = new AtomicReference<>(0);
    List<Integer> sizes = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      int records = (int) request.lines().filter(line -> line.contains("\"_index\"")).count();
      sizes.add(records);
      requests.updateAndGet(value -> value + 1);
      StringBuilder response = new StringBuilder("{\"errors\":false,\"items\":[");
      for (int index = 0; index < records; index++) {
        if (index > 0) response.append(',');
        response.append("{\"index\":{\"status\":201}}");
      }
      respond(exchange, response + "]}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      List<FileAsset> files = new ArrayList<>();
      for (int index = 0; index < 101; index++) files.add(file("s3://survey/bulk-" + index + ".fits"));
      adapter.upsertBatch(files, List.of());
      assertEquals(2, requests.get());
      assertEquals(List.of(100, 1), sizes);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void installsAndVerifiesThreeStrictMappings() throws Exception {
    List<String> paths = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      paths.add(path);
      if (path.endsWith("/_mapping")) {
        String index = path.substring(1, path.indexOf("/_mapping"));
        respond(exchange, mappingJson(index));
      } else respond(exchange, "{\"acknowledged\":true}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      adapter.installIndexTemplates();
      adapter.verifyIndexMappings();
      assertEquals(3, paths.stream().filter(path -> path.startsWith("/_index_template/")).count());
      assertEquals(3, paths.stream().filter(path -> path.endsWith("/_mapping")).count());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void recreatesOnlyTheThreeAstIndices() throws Exception {
    List<String> paths = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
      exchange.getRequestBody().readAllBytes();
      respond(exchange, "{\"acknowledged\":true}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = adapter(server)) {
      adapter.recreateFixedIndices();
      assertEquals(6, paths.size());
      assertTrue(paths.contains("DELETE /" + IndexContract.LAYER_INDEX));
      assertTrue(paths.contains("DELETE /" + IndexContract.FILE_INDEX));
      assertTrue(paths.contains("DELETE /" + IndexContract.COVERAGE_INDEX));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportsPermanentBulkIdsWithoutCredentialMaterial() throws Exception {
    FileAsset file = file("s3://survey/bad.fits");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(!request.contains("secret"));
      respond(exchange, "{\"errors\":true,\"items\":[{\"index\":{\"status\":400}}]}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), "user", "secret")) {
      ElasticsearchAdapter.BulkWriteException failure = assertThrows(ElasticsearchAdapter.BulkWriteException.class,
          () -> adapter.upsertFileAsset(file));
      assertEquals(List.of(file.fileId()), failure.failedDocumentIds());
      assertTrue(!failure.getMessage().contains("secret"));
    } finally {
      server.stop(0);
    }
  }

  private static ElasticsearchAdapter adapter(HttpServer server) {
    return new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), null, null);
  }

  private static FileAsset file(String uri) {
    return new FileAsset(SourceIdentity.fileId(uri), uri, uri.substring(uri.lastIndexOf('/') + 1), "s3://survey",
        FileType.FITS, 10L, null, Instant.parse("2026-01-02T03:04:05Z"));
  }

  private static SpatialCoverage coverage(String layerId, FileAsset file, int order, long cell) {
    return new SpatialCoverage(layerId, file.fileId(), file.sourceUri(), order, cell, CoordinateFrame.ICRS,
        HealpixNesting.NESTED, CoverageMethod.FITS_WCS, CoverageRole.FOOTPRINT, Modality.IMAGE,
        CoveragePrecision.ESTIMATED, null);
  }

  private static CoverageLayer activeLayer(String layerId, List<Integer> orders) {
    CoverageLayer updating = CoverageLayer.updating(layerSpec(layerId), "run-1", Instant.now().plusSeconds(60));
    return updating.active("snapshot", orders, 1, 1, 0);
  }

  private static LayerSpec layerSpec(String layerId) {
    return new LayerSpec(layerId, "survey", "release", "product", Modality.IMAGE, CoverageRole.FOOTPRINT, "https://example.invalid");
  }

  private static void handle(HttpExchange exchange, CoverageLayer layer, FileAsset file,
      SpatialCoverage coverage, AtomicReference<String> bulk) throws java.io.IOException {
    String path = exchange.getRequestURI().getPath();
    String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (path.endsWith("/_bulk")) {
      bulk.set(request);
      int records = (int) request.lines().filter(line -> line.contains("\"_index\"")).count();
      StringBuilder response = new StringBuilder("{\"errors\":false,\"items\":[");
      for (int index = 0; index < records; index++) {
        if (index > 0) response.append(',');
        response.append("{\"index\":{\"status\":201}}");
      }
      respond(exchange, response + "]}");
    } else if (path.contains("/_mget") && path.startsWith("/" + IndexContract.LAYER_INDEX)) {
      respond(exchange, "{\"docs\":[{\"found\":true,\"_source\":" + layerJson(layer) + "}]}");
    } else if (path.contains("/_mget")) {
      respond(exchange, "{\"docs\":[{\"found\":true,\"_source\":" + fileJson(file) + "}]}");
    } else if (path.endsWith("/_search")) {
      respond(exchange, "{\"hits\":{\"hits\":[{\"sort\":[\"" + layer.layerId() + "\",\"" + file.fileId()
          + "\"," + coverage.healpixCell() + ",\"footprint\"],\"_source\":" + coverageJson(coverage) + "}]}}");
    } else if (path.contains("/_doc/")) {
      respond(exchange, "{\"found\":true,\"_source\":" + layerJson(layer) + "}");
    } else respond(exchange, "{\"acknowledged\":true}");
  }

  private static String layerJson(CoverageLayer layer) {
    return "{\"layer_id\":\"" + layer.layerId() + "\",\"survey_id\":\"" + layer.surveyId()
        + "\",\"release_id\":\"" + layer.releaseId() + "\",\"product_id\":\"" + layer.productId()
        + "\",\"modality\":\"" + layer.modality().value() + "\",\"coverage_role\":\""
        + layer.coverageRole().value() + "\",\"entrypoint\":\"https://example.invalid\",\"state\":\""
        + layer.state().value() + "\",\"scan_run_id\":\"" + layer.scanRunId()
        + "\",\"lease_expires_at\":" + jsonString(layer.leaseExpiresAt()) + ",\"source_snapshot_sha256\":\"snapshot\""
        + ",\"available_orders\":" + layer.availableOrders() + ",\"file_count\":1,\"coverage_count\":1,\"error_count\":0"
        + ",\"error_summary\":null,\"updated_at\":\"" + layer.updatedAt() + "\"}";
  }

  private static String coverageJson(SpatialCoverage coverage) {
    return "{\"layer_id\":\"" + coverage.layerId() + "\",\"source_file_id\":\"" + coverage.sourceFileId()
        + "\",\"source_uri\":\"" + coverage.sourceUri() + "\",\"healpix_order\":" + coverage.healpixOrder()
        + ",\"healpix_cell\":" + coverage.healpixCell() + ",\"coverage_method\":\""
        + coverage.coverageMethod().value() + "\",\"coverage_role\":\"" + coverage.coverageRole().value()
        + "\",\"modality\":\"" + coverage.modality().value() + "\",\"precision\":\""
        + coverage.precision().value() + "\",\"source_order\":null}";
  }

  private static String fileJson(FileAsset file) {
    return "{\"file_id\":\"" + file.fileId() + "\",\"source_uri\":\"" + file.sourceUri()
        + "\",\"file_name\":\"" + file.fileName() + "\",\"parent_uri\":\"" + file.parentUri()
        + "\",\"file_type\":\"FITS\",\"size_bytes\":10,\"last_modified\":null,\"indexed_at\":\""
        + file.indexedAt() + "\"}";
  }

  private static String jsonString(Instant value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static void respond(HttpExchange exchange, String response) throws java.io.IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static String mappingJson(String index) {
    String properties;
    if (IndexContract.LAYER_INDEX.equals(index)) {
      properties = "\"layer_id\":{\"type\":\"keyword\"},\"survey_id\":{\"type\":\"keyword\"},"
          + "\"release_id\":{\"type\":\"keyword\"},\"product_id\":{\"type\":\"keyword\"},"
          + "\"modality\":{\"type\":\"keyword\"},\"coverage_role\":{\"type\":\"keyword\"},"
          + "\"entrypoint\":{\"type\":\"keyword\"},\"state\":{\"type\":\"keyword\"},"
          + "\"scan_run_id\":{\"type\":\"keyword\"},\"lease_expires_at\":{\"type\":\"date\"},"
          + "\"source_snapshot_sha256\":{\"type\":\"keyword\"},\"available_orders\":{\"type\":\"integer\"},"
          + "\"file_count\":{\"type\":\"long\"},\"coverage_count\":{\"type\":\"long\"},"
          + "\"error_count\":{\"type\":\"integer\"},\"error_summary\":{\"type\":\"keyword\"},"
          + "\"updated_at\":{\"type\":\"date\"}";
    } else if (IndexContract.FILE_INDEX.equals(index)) {
      properties = "\"file_id\":{\"type\":\"keyword\"},\"source_uri\":{\"type\":\"keyword\"},"
          + "\"file_name\":{\"type\":\"keyword\"},\"parent_uri\":{\"type\":\"keyword\"},"
          + "\"file_type\":{\"type\":\"keyword\"},\"size_bytes\":{\"type\":\"long\"},"
          + "\"last_modified\":{\"type\":\"date\"},\"indexed_at\":{\"type\":\"date\"}";
    } else {
      properties = "\"layer_id\":{\"type\":\"keyword\"},\"source_file_id\":{\"type\":\"keyword\"},"
          + "\"source_uri\":{\"type\":\"keyword\"},\"healpix_order\":{\"type\":\"integer\"},"
          + "\"healpix_cell\":{\"type\":\"long\"},\"coordinate_frame\":{\"type\":\"keyword\"},"
          + "\"nesting\":{\"type\":\"keyword\"},\"coverage_method\":{\"type\":\"keyword\"},"
          + "\"coverage_role\":{\"type\":\"keyword\"},\"modality\":{\"type\":\"keyword\"},"
          + "\"precision\":{\"type\":\"keyword\"},\"source_order\":{\"type\":\"integer\"}";
    }
    return "{\"" + index + "\":{\"mappings\":{\"dynamic\":\"strict\",\"properties\":{" + properties + "}}}}";
  }
}
