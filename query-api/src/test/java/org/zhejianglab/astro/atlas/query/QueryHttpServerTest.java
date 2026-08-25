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
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;
import org.zhejianglab.astro.atlas.core.SourceIdentity;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

class QueryHttpServerTest {
  @Test
  void servesHealthReadinessAndPointSearch() throws Exception {
    String sourceUri = "s3://survey/image.fits";
    String fileId = SourceIdentity.fileId(sourceUri);
    long cell = Healpix.ang2pixNest(8, 180.25, -2.5);
    FileAsset file = new FileAsset(fileId, sourceUri, "image.fits", "s3://survey", FileType.FITS,
        10L, null, Modality.of("image"), SpatialStatus.KNOWN, List.of(cell), Instant.now());
    SpatialCoverage coverage = new SpatialCoverage(fileId, sourceUri, 8, cell, CoordinateFrame.ICRS,
        HealpixNesting.NESTED, CoverageMethod.WCS, CoverageRole.FOOTPRINT, Modality.of("image"), null);

    IndexReader reader = new IndexReader() {
      @Override
      public Page<SpatialCoverage> searchCoverage(Collection<Long> cells, int limit, String cursor) {
        return new Page<>(List.of(coverage), null);
      }

      @Override
      public Collection<FileAsset> findFiles(Collection<String> ids) {
        return List.of(file);
      }
    };

    try (QueryHttpServer server = new QueryHttpServer("127.0.0.1", 0, new QueryService(reader))) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpResponse<String> health = get(client, server, "/healthz");
      assertEquals(200, health.statusCode());
      assertTrue(health.body().contains("\"status\":\"ok\""));

      HttpResponse<String> ready = get(client, server, "/readyz");
      assertEquals(200, ready.statusCode());
      assertTrue(ready.body().contains("\"status\":\"ready\""));

      HttpResponse<String> search = get(client, server, "/v1/files/point?ra=180.25&dec=-2.5");
      assertEquals(200, search.statusCode());
      assertTrue(search.body().contains(fileId));
      assertTrue(search.body().contains("\"matchingCoverage\":[{"));

      HttpResponse<String> invalid = get(client, server, "/v1/files/point?ra=180.25");
      assertEquals(400, invalid.statusCode());
      assertTrue(invalid.body().contains("\"code\":\"INVALID_QUERY\""));
    }
  }

  private static HttpResponse<String> get(HttpClient client, QueryHttpServer server, String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
        .GET()
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
