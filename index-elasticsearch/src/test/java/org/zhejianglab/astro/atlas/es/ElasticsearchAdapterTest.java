package org.zhejianglab.astro.atlas.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.SourceIdentity;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

class ElasticsearchAdapterTest {
  @Test
  void writesFixedIndicesAndReadsCoverageThenFile() throws Exception {
    String sourceUri = "s3://survey/image.fits";
    String fileId = SourceIdentity.fileId(sourceUri);
    long cell = Healpix.ang2pixNest(8, 180.25, -2.5);
    FileAsset file = new FileAsset(fileId, sourceUri, "image.fits", "s3://survey", FileType.FITS,
        10L, null, Modality.of("image"), SpatialStatus.KNOWN, List.of(cell), Instant.parse("2026-01-02T03:04:05Z"));
    SpatialCoverage coverage = new SpatialCoverage(fileId, sourceUri, 8, cell, CoordinateFrame.ICRS,
        HealpixNesting.NESTED, CoverageMethod.WCS, CoverageRole.FOOTPRINT, Modality.of("image"), null);
    AtomicReference<String> lastBody = new AtomicReference<>();
    AtomicInteger bulkRequests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> handle(exchange, file, coverage, lastBody, bulkRequests));
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), "user", "secret")) {
      adapter.upsertFileAsset(file);
      assertTrue(lastBody.get().contains("ast_file_index_v1"));
      assertTrue(lastBody.get().contains(fileId));
      assertTrue(!lastBody.get().contains("secret"));

      adapter.upsertCoverage(coverage);
      assertTrue(lastBody.get().contains("ast_coverage_index_v1"));
      assertTrue(lastBody.get().contains(coverage.id()));

      var page = adapter.searchCoverage(List.of(cell), 1, null);
      assertEquals(1, page.items().size());
      assertEquals(fileId, page.items().get(0).sourceFileId());
      assertTrue(page.nextCursor() != null);

      var files = adapter.findFiles(List.of(fileId));
      assertEquals(1, files.size());
      assertEquals(fileId, files.iterator().next().fileId());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void retriesOnlyRetryableBulkItems() throws Exception {
    String sourceUri = "s3://survey/retry.fits";
    FileAsset file = file(sourceUri);
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      exchange.getRequestBody().readAllBytes();
      int request = requests.incrementAndGet();
      String response = request == 1
          ? "{\"errors\":true,\"items\":[{\"index\":{\"status\":429}}]}"
          : "{\"errors\":false,\"items\":[{\"index\":{\"status\":201}}]}";
      respond(exchange, response);
    });
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), null, null)) {
      adapter.upsertFileAsset(file);
      assertEquals(2, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void splitsBulkRequestsAtTheRecordLimit() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    List<Integer> requestSizes = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      int records = (int) request.lines().filter(line -> line.contains("\"_index\"" )).count();
      requestSizes.add(records);
      requests.incrementAndGet();
      StringBuilder response = new StringBuilder("{\"errors\":false,\"items\":[");
      for (int index = 0; index < records; index++) {
        if (index > 0) response.append(',');
        response.append("{\"index\":{\"status\":201}}");
      }
      response.append("]}");
      respond(exchange, response.toString());
    });
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), null, null)) {
      List<FileAsset> files = new ArrayList<>();
      for (int index = 0; index < 501; index++) files.add(file("s3://survey/bulk-" + index + ".fits"));
      adapter.upsertBatch(files, List.of());
      assertEquals(2, requests.get());
      assertEquals(List.of(500, 1), requestSizes);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void installsTemplatesAndVerifiesStrictMappings() throws Exception {
    AtomicInteger templateRequests = new AtomicInteger();
    AtomicInteger mappingRequests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      if (path.startsWith("/_index_template/")) {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"dynamic\":\"strict\""));
        templateRequests.incrementAndGet();
        respond(exchange, "{\"acknowledged\":true}");
        return;
      }
      if (path.endsWith("/_mapping")) {
        mappingRequests.incrementAndGet();
        String index = path.startsWith("/" + org.zhejianglab.astro.atlas.core.IndexContract.FILE_INDEX)
            ? org.zhejianglab.astro.atlas.core.IndexContract.FILE_INDEX
            : org.zhejianglab.astro.atlas.core.IndexContract.COVERAGE_INDEX;
        respond(exchange, mappingJson(index));
        return;
      }
      respond(exchange, "{\"status\":\"ok\"}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), null, null)) {
      adapter.installIndexTemplates();
      adapter.verifyIndexMappings();
      assertEquals(2, templateRequests.get());
      assertEquals(2, mappingRequests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void recreatesOnlyFixedIndicesWithZeroReplicas() throws Exception {
    List<String> paths = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
      exchange.getRequestBody().readAllBytes();
      respond(exchange, "{\"acknowledged\":true}");
    });
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), null, null)) {
      adapter.recreateFixedIndices();
      assertEquals(List.of(
          "DELETE /ast_file_index_v1",
          "DELETE /ast_coverage_index_v1",
          "PUT /ast_file_index_v1",
          "PUT /ast_coverage_index_v1"), paths);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportsPermanentBulkItemIdsWithoutExposingCredentials() throws Exception {
    String sourceUri = "s3://survey/bad.fits";
    FileAsset file = file(sourceUri);
    AtomicBoolean sawSecret = new AtomicBoolean();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      sawSecret.set(request.contains("secret"));
      respond(exchange, "{\"errors\":true,\"items\":[{\"index\":{\"status\":400,\"error\":{\"type\":\"mapper_parsing_exception\"}}}]}" );
    });
    server.start();
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter("http://127.0.0.1:" + server.getAddress().getPort(), "user", "secret")) {
      ElasticsearchAdapter.BulkWriteException failure = assertThrows(
          ElasticsearchAdapter.BulkWriteException.class, () -> adapter.upsertFileAsset(file));
      assertEquals(1, failure.failedRecordCount());
      assertTrue(failure.failedDocumentIds().contains(file.fileId()));
      assertTrue(!failure.getMessage().contains("secret"));
      assertTrue(!sawSecret.get());
    } finally {
      server.stop(0);
    }
  }

  private static FileAsset file(String sourceUri) {
    return new FileAsset(SourceIdentity.fileId(sourceUri), sourceUri, "fixture.fits", "s3://survey",
        FileType.FITS, 10L, null, Modality.of("image"), SpatialStatus.UNKNOWN, List.of(), Instant.now());
  }

  private static void respond(HttpExchange exchange, String response) throws java.io.IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static String mappingJson(String index) {
    boolean file = index.equals(org.zhejianglab.astro.atlas.core.IndexContract.FILE_INDEX);
    String properties = file
        ? "\"file_id\":{\"type\":\"keyword\"},\"source_uri\":{\"type\":\"keyword\"},"
            + "\"file_name\":{\"type\":\"keyword\"},\"parent_uri\":{\"type\":\"keyword\"},"
            + "\"file_type\":{\"type\":\"keyword\"},\"size_bytes\":{\"type\":\"long\"},"
            + "\"last_modified\":{\"type\":\"date\"},\"modality\":{\"type\":\"keyword\"},"
            + "\"spatial_status\":{\"type\":\"keyword\"},\"coverage_cells\":{\"type\":\"integer\"},"
            + "\"indexed_at\":{\"type\":\"date\"}"
        : "\"source_file_id\":{\"type\":\"keyword\"},\"source_uri\":{\"type\":\"keyword\"},"
            + "\"healpix_order\":{\"type\":\"integer\"},\"healpix_cell\":{\"type\":\"long\"},"
            + "\"coordinate_frame\":{\"type\":\"keyword\"},\"nesting\":{\"type\":\"keyword\"},"
            + "\"coverage_method\":{\"type\":\"keyword\"},\"coverage_role\":{\"type\":\"keyword\"},"
            + "\"modality\":{\"type\":\"keyword\"},\"quality\":{\"type\":\"keyword\"}";
    return "{\"" + index + "\":{\"mappings\":{\"dynamic\":\"strict\",\"properties\":{" + properties + "}}}}";
  }

  private static void handle(HttpExchange exchange, FileAsset file, SpatialCoverage coverage,
      AtomicReference<String> body, AtomicInteger bulkRequests) throws java.io.IOException {
    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    body.set(requestBody);
    String path = exchange.getRequestURI().getPath();
    String response;
    if (path.endsWith("/_bulk")) {
      bulkRequests.incrementAndGet();
      response = "{\"errors\":false,\"items\":[{\"index\":{\"status\":201}}]}";
    } else if (path.endsWith("/_search")) {
      response = "{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"hits\":[{\"_id\":\"" + coverage.id() + "\",\"sort\":[\"" + file.fileId() + "\"," + coverage.healpixCell() + ",\"footprint\"],\"_source\":" + jsonCoverage(coverage) + "}]}}";
    } else if (path.endsWith("/_mget")) {
      response = "{\"docs\":[{\"found\":true,\"_source\":" + jsonFile(file) + "}]}";
    } else {
      response = "{\"status\":\"ok\"}";
    }
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
  }

  private static String jsonCoverage(SpatialCoverage coverage) {
    return "{\"source_file_id\":\"" + coverage.sourceFileId() + "\",\"source_uri\":\"" + coverage.sourceUri()
        + "\",\"healpix_order\":8,\"healpix_cell\":" + coverage.healpixCell()
        + ",\"coverage_method\":\"wcs\",\"coverage_role\":\"footprint\",\"modality\":\"image\",\"quality\":null}";
  }

  private static String jsonFile(FileAsset file) {
    return "{\"file_id\":\"" + file.fileId() + "\",\"source_uri\":\"" + file.sourceUri()
        + "\",\"file_name\":\"image.fits\",\"parent_uri\":\"s3://survey\",\"file_type\":\"FITS\",\"size_bytes\":10"
        + ",\"last_modified\":null,\"modality\":\"image\",\"spatial_status\":\"known\",\"coverage_cells\":[" + file.coverageCells().get(0)
        + "],\"indexed_at\":\"2026-01-02T03:04:05Z\"}";
  }
}
