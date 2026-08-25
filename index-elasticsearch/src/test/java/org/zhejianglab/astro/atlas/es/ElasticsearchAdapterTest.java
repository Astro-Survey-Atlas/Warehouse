package org.zhejianglab.astro.atlas.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> handle(exchange, file, coverage, lastBody));
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

  private static void handle(HttpExchange exchange, FileAsset file, SpatialCoverage coverage, AtomicReference<String> body) throws java.io.IOException {
    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    body.set(requestBody);
    String path = exchange.getRequestURI().getPath();
    String response;
    if (path.endsWith("/_bulk")) {
      response = "{\"errors\":false,\"items\":[]}";
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
