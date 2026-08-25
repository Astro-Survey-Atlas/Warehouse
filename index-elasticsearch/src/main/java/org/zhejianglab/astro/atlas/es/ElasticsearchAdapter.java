package org.zhejianglab.astro.atlas.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.IndexContract;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

/** HTTP adapter writing fixed indices and serving read-only coverage-to-file queries. */
public final class ElasticsearchAdapter implements IndexWriter, IndexReader, AutoCloseable {
  private final URI endpoint;
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final String authorization;

  public ElasticsearchAdapter(String endpoint, String username, String password) {
    this.endpoint = normalize(endpoint);
    this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    if (username != null && !username.isBlank()) {
      String token = username + ":" + (password == null ? "" : password);
      this.authorization = "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    } else {
      this.authorization = null;
    }
  }

  @Override
  public void upsertFileAsset(FileAsset fileAsset) {
    indexDocument(IndexContract.FILE_INDEX, fileAsset.fileId(), fileAsset.toDocument());
  }

  @Override
  public void upsertCoverage(SpatialCoverage coverage) {
    indexDocument(IndexContract.COVERAGE_INDEX, coverage.id(), coverage.toDocument());
  }

  @Override
  public Page<SpatialCoverage> searchCoverage(Collection<Long> order8Cells, int limit, String cursor) {
    if (order8Cells == null || order8Cells.isEmpty()) return new Page<>(List.of(), null);
    List<Long> cells = order8Cells.stream().sorted().toList();
    Map<String, Object> terms = new LinkedHashMap<>();
    terms.put("healpix_cell", cells);
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("query", Map.of("terms", terms));
    query.put("sort", List.of(
        Map.of("source_file_id.keyword", "asc"),
        Map.of("healpix_cell", "asc"),
        Map.of("coverage_role.keyword", "asc")));
    query.put("size", limit);
    Cursor after = decodeCursor(cursor, cells);
    if (after != null) query.put("search_after", List.of(after.fileId(), after.cell(), after.role()));

    JsonNode root = send("POST", "/" + IndexContract.COVERAGE_INDEX + "/_search", query);
    JsonNode hits = root.path("hits").path("hits");
    List<SpatialCoverage> coverages = new ArrayList<>();
    Cursor last = null;
    for (JsonNode hit : hits) {
      JsonNode source = hit.path("_source");
      String fileId = source.path("source_file_id").asText();
      String uri = source.path("source_uri").asText();
      String methodValue = source.path("coverage_method").asText();
      String roleValue = source.path("coverage_role").asText();
      String modalityValue = optionalText(source, "modality");
      String quality = optionalText(source, "quality");
      coverages.add(new SpatialCoverage(
          fileId,
          uri,
          source.path("healpix_order").asInt(),
          source.path("healpix_cell").asLong(),
          CoordinateFrame.ICRS,
          HealpixNesting.NESTED,
          CoverageMethod.valueOf(methodValue.toUpperCase(Locale.ROOT)),
          CoverageRole.valueOf(roleValue.toUpperCase(Locale.ROOT)),
          Modality.of(modalityValue),
          quality));
      JsonNode sortValues = hit.path("sort");
      if (sortValues.isArray() && sortValues.size() >= 3) {
        last = new Cursor(sortValues.get(0).asText(), sortValues.get(1).asLong(), sortValues.get(2).asText(), cellHash(cells));
      }
    }
    boolean hasMore = hits.size() == limit && last != null;
    return new Page<>(coverages, hasMore ? encodeCursor(last) : null);
  }

  @Override
  public Collection<FileAsset> findFiles(Collection<String> fileIds) {
    if (fileIds == null || fileIds.isEmpty()) return List.of();
    JsonNode root = send("POST", "/" + IndexContract.FILE_INDEX + "/_mget", Map.of("ids", new ArrayList<>(fileIds)));
    List<FileAsset> files = new ArrayList<>();
    for (JsonNode document : root.path("docs")) {
      if (document.path("found").asBoolean()) files.add(readFileAsset(document.path("_source")));
    }
    return files;
  }

  @Override
  public boolean isReady() {
    try {
      HttpRequest request = request("GET", "/_cluster/health?wait_for_status=yellow&timeout=1s", null).build();
      return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException exception) {
      return false;
    }
  }

  @Override
  public void close() {
    // java.net.http.HttpClient does not require explicit shutdown.
  }

  private void indexDocument(String index, String id, Map<String, Object> document) {
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        String action = mapper.writeValueAsString(Map.of("index", Map.of("_index", index, "_id", id)));
        String payload = action + "\n" + mapper.writeValueAsString(document) + "\n";
        HttpResponse<String> response = client.send(
            request("POST", "/_bulk?refresh=wait_for", payload).build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 500 && attempt < 3) continue;
        if (response.statusCode() >= 300) throw new IllegalStateException("Elasticsearch bulk request failed with status " + response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        if (body.path("errors").asBoolean(false)) throw new IllegalStateException("Elasticsearch bulk request contained an item failure");
        return;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Elasticsearch bulk request interrupted", exception);
      } catch (IOException exception) {
        if (attempt == 3) throw new IllegalStateException("Elasticsearch bulk request failed", exception);
      }
    }
  }

  private JsonNode send(String method, String path, Map<String, Object> body) {
    try {
      String payload = body == null ? null : mapper.writeValueAsString(body);
      HttpResponse<String> response = client.send(
          request(method, path, payload).build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) throw new IllegalStateException("Elasticsearch request failed with status " + response.statusCode());
      return mapper.readTree(response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Elasticsearch request interrupted", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Elasticsearch request failed", exception);
    }
  }

  private HttpRequest.Builder request(String method, String path, String body) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
        .timeout(Duration.ofSeconds(30));
    if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
    else request.method(method, HttpRequest.BodyPublishers.ofString(body));
    request.header("Content-Type", "application/json");
    if (authorization != null) request.header("Authorization", authorization);
    return request;
  }

  private URI uri(String path) {
    return URI.create(endpoint.toString() + path);
  }

  private static URI normalize(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("Elasticsearch endpoint is required");
    String value = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    return URI.create(value);
  }

  private FileAsset readFileAsset(JsonNode source) {
    List<Long> cells = new ArrayList<>();
    for (JsonNode cell : source.path("coverage_cells")) cells.add(cell.asLong());
    return new FileAsset(
        source.path("file_id").asText(),
        source.path("source_uri").asText(),
        source.path("file_name").asText(),
        optionalText(source, "parent_uri"),
        FileType.valueOf(source.path("file_type").asText()),
        source.path("size_bytes").isNull() ? null : source.path("size_bytes").asLong(),
        optionalInstant(source, "last_modified"),
        Modality.of(optionalText(source, "modality")),
        SpatialStatus.valueOf(source.path("spatial_status").asText().toUpperCase(Locale.ROOT)),
        cells,
        Instant.parse(source.path("indexed_at").asText()));
  }

  private static String optionalText(JsonNode source, String field) {
    JsonNode value = source.get(field);
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }

  private static Instant optionalInstant(JsonNode source, String field) {
    String value = optionalText(source, field);
    return value == null ? null : Instant.parse(value);
  }

  private static String cellHash(Collection<Long> cells) {
    try {
      String canonical = cells.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String encodeCursor(Cursor cursor) {
    String raw = cursor.hash() + "|" + cursor.fileId() + "|" + cursor.cell() + "|" + cursor.role();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decodeCursor(String encoded, Collection<Long> cells) {
    if (encoded == null || encoded.isBlank()) return null;
    try {
      String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", -1);
      if (parts.length != 4 || !cellHash(cells).equals(parts[0])) throw new IllegalArgumentException("cursor does not match query");
      return new Cursor(parts[1], Long.parseLong(parts[2]), parts[3], parts[0]);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("malformed cursor");
    }
  }

  private record Cursor(String fileId, long cell, String role, String hash) {}
}
