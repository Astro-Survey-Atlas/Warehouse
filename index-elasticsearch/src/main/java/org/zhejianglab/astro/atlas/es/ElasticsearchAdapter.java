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
  public static final int BULK_MAX_RECORDS = 500;
  public static final long BULK_MAX_BYTES = 1_500_000L;
  private static final int MAX_RETRIES = 3;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
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
    upsertBatch(List.of(fileAsset), List.of());
  }

  @Override
  public void upsertCoverage(SpatialCoverage coverage) {
    upsertBatch(List.of(), List.of(coverage));
  }

  @Override
  public void upsertBatch(Collection<FileAsset> fileAssets, Collection<SpatialCoverage> coverages) {
    List<BulkOperation> operations = new ArrayList<>();
    if (fileAssets != null) {
      for (FileAsset fileAsset : fileAssets) {
        if (fileAsset != null) operations.add(operation(IndexContract.FILE_INDEX, fileAsset.fileId(), fileAsset.toDocument()));
      }
    }
    if (coverages != null) {
      for (SpatialCoverage coverage : coverages) {
        if (coverage != null) operations.add(operation(IndexContract.COVERAGE_INDEX, coverage.id(), coverage.toDocument()));
      }
    }
    List<BulkOperation> batch = new ArrayList<>();
    long batchBytes = 0L;
    for (BulkOperation operation : operations) {
      long operationBytes = operation.ndjson().getBytes(StandardCharsets.UTF_8).length;
      if (operationBytes > BULK_MAX_BYTES) {
        throw new BulkWriteException("Elasticsearch bulk document exceeds " + BULK_MAX_BYTES + " bytes",
            List.of(operation.id()));
      }
      if (!batch.isEmpty() && (batch.size() >= BULK_MAX_RECORDS || batchBytes + operationBytes > BULK_MAX_BYTES)) {
        sendBulk(batch);
        batch = new ArrayList<>();
        batchBytes = 0L;
      }
      batch.add(operation);
      batchBytes += operationBytes;
    }
    if (!batch.isEmpty()) sendBulk(batch);
  }

  /** Installs templates only; it never creates an index or changes an existing mapping. */
  public void installIndexTemplates() {
    putJson("/_index_template/" + ElasticsearchIndexTemplates.FILE_TEMPLATE_NAME,
        ElasticsearchIndexTemplates.fileTemplate());
    putJson("/_index_template/" + ElasticsearchIndexTemplates.COVERAGE_TEMPLATE_NAME,
        ElasticsearchIndexTemplates.coverageTemplate());
  }

  /** Verifies that both fixed indices exist and contain the required field types. */
  public void verifyIndexMappings() {
    verifyMapping(IndexContract.FILE_INDEX, ElasticsearchIndexTemplates.fileMappings());
    verifyMapping(IndexContract.COVERAGE_INDEX, ElasticsearchIndexTemplates.coverageMappings());
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
         Map.of("source_file_id", "asc"),
         Map.of("healpix_cell", "asc"),
         Map.of("coverage_role", "asc")));
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

  private void sendBulk(List<BulkOperation> original) {
    List<BulkOperation> pending = List.copyOf(original);
    for (int retry = 0; !pending.isEmpty(); retry++) {
      HttpResponse<String> response;
      try {
        response = client.send(
            request("POST", "/_bulk?refresh=wait_for", ndjson(pending)).build(),
            HttpResponse.BodyHandlers.ofString());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new BulkWriteException("Elasticsearch bulk request interrupted", ids(pending), pending.size(), exception);
      } catch (IOException exception) {
        if (retry >= MAX_RETRIES) {
          throw new BulkWriteException("Elasticsearch bulk transport retries exhausted", ids(pending), pending.size(), exception);
        }
        pause(retry);
        continue;
      }
      if (response.statusCode() / 100 != 2) {
        if (retryable(response.statusCode()) && retry < MAX_RETRIES) {
          pause(retry);
          continue;
        }
        throw new BulkWriteException("Elasticsearch bulk request failed with status " + response.statusCode(), ids(pending), pending.size());
      }
      BulkResponse bulkResponse = parseBulkResponse(response.body(), pending);
      if (!bulkResponse.permanentIds().isEmpty()) {
        throw new BulkWriteException("Elasticsearch bulk item failure", sampleIds(bulkResponse.permanentIds()),
            bulkResponse.permanentIds().size());
      }
      if (bulkResponse.retryable().isEmpty()) return;
      if (retry >= MAX_RETRIES) {
        throw new BulkWriteException("Elasticsearch bulk item retries exhausted", ids(bulkResponse.retryable()),
            bulkResponse.retryable().size());
      }
      pending = List.copyOf(bulkResponse.retryable());
      pause(retry);
    }
  }

  private BulkResponse parseBulkResponse(String body, List<BulkOperation> operations) {
    final JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (IOException exception) {
      throw new BulkWriteException("Elasticsearch bulk returned invalid JSON", ids(operations), operations.size(), exception);
    }
    JsonNode items = root == null ? null : root.get("items");
    if (items == null || !items.isArray() || items.size() != operations.size()) {
      throw new BulkWriteException("Elasticsearch bulk response item count mismatch", ids(operations), operations.size());
    }
    List<BulkOperation> retryable = new ArrayList<>();
    List<String> permanentIds = new ArrayList<>();
    for (int index = 0; index < operations.size(); index++) {
      JsonNode action = firstChild(items.get(index));
      int status = action == null ? 500 : action.path("status").asInt(500);
      if (status >= 200 && status < 300) continue;
      if (retryable(status)) retryable.add(operations.get(index));
      else permanentIds.add(operations.get(index).id());
    }
    return new BulkResponse(retryable, permanentIds);
  }

  private static JsonNode firstChild(JsonNode object) {
    if (object == null || !object.isObject() || !object.fields().hasNext()) return null;
    return object.fields().next().getValue();
  }

  private void putJson(String path, Map<String, Object> body) {
    try {
      HttpResponse<String> response = client.send(
          request("PUT", path, mapper.writeValueAsString(body)).build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        throw new IllegalStateException("Elasticsearch index template request failed with status " + response.statusCode());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Elasticsearch index template request interrupted", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Elasticsearch index template request failed", exception);
    }
  }

  private void verifyMapping(String index, Map<String, Object> expectedMapping) {
    JsonNode root = send("GET", "/" + index + "/_mapping", null);
    JsonNode actual = root.path(index).path("mappings");
    if (!"strict".equals(actual.path("dynamic").asText())) {
      throw new IllegalStateException("Elasticsearch mapping for " + index + " must use dynamic=strict");
    }
    JsonNode properties = actual.path("properties");
    JsonNode expectedProperties = mapper.valueToTree(expectedMapping).path("properties");
    expectedProperties.fields().forEachRemaining(entry -> {
      JsonNode actualField = properties.path(entry.getKey());
      String expectedType = entry.getValue().path("type").asText();
      if (!actualField.isObject() || !expectedType.equals(actualField.path("type").asText())) {
        throw new IllegalStateException("Elasticsearch mapping for " + index + " has incompatible field " + entry.getKey());
      }
    });
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

  private BulkOperation operation(String index, String id, Map<String, Object> document) {
    try {
      String action = mapper.writeValueAsString(Map.of("index", Map.of("_index", index, "_id", id)));
      return new BulkOperation(index, id, action + "\n" + mapper.writeValueAsString(document) + "\n");
    } catch (IOException exception) {
      throw new IllegalStateException("failed to serialize Elasticsearch bulk document", exception);
    }
  }

  private static String ndjson(List<BulkOperation> operations) {
    StringBuilder payload = new StringBuilder();
    operations.forEach(operation -> payload.append(operation.ndjson()));
    return payload.toString();
  }

  private static List<String> ids(Collection<BulkOperation> operations) {
    return operations.stream().map(BulkOperation::id).limit(10).toList();
  }

  private static List<String> sampleIds(Collection<String> ids) {
    return ids.stream().limit(10).toList();
  }

  private static boolean retryable(int status) {
    return status == 408 || status == 409 || status == 425 || status == 429 || status >= 500;
  }

  private static void pause(int retry) {
    try {
      Thread.sleep(Math.min(1_000L, 50L << retry));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Elasticsearch bulk retry interrupted", exception);
    }
  }

  private HttpRequest.Builder request(String method, String path, String body) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
         .timeout(REQUEST_TIMEOUT);
    if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
    else request.method(method, HttpRequest.BodyPublishers.ofString(body));
    request.header("Content-Type", path.contains("/_bulk") ? "application/x-ndjson" : "application/json");
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

  private record BulkOperation(String index, String id, String ndjson) {}

  private record BulkResponse(List<BulkOperation> retryable, List<String> permanentIds) {}

  public static final class BulkWriteException extends IllegalStateException {
    private final List<String> failedDocumentIds;
    private final int failedRecordCount;

    public BulkWriteException(String message, List<String> failedDocumentIds) {
      this(message, failedDocumentIds, failedDocumentIds.size(), null);
    }

    public BulkWriteException(String message, List<String> failedDocumentIds, int failedRecordCount) {
      this(message, failedDocumentIds, failedRecordCount, null);
    }

    public BulkWriteException(String message, List<String> failedDocumentIds, int failedRecordCount, Throwable cause) {
      super(message + ": failedRecordCount=" + failedRecordCount + ", failedDocumentIds=" + failedDocumentIds, cause);
      this.failedDocumentIds = List.copyOf(failedDocumentIds);
      if (failedRecordCount < this.failedDocumentIds.size()) {
        throw new IllegalArgumentException("failedRecordCount cannot be smaller than sampled document IDs");
      }
      this.failedRecordCount = failedRecordCount;
    }

    public int failedRecordCount() {
      return failedRecordCount;
    }

    public List<String> failedDocumentIds() {
      return failedDocumentIds;
    }
  }
}
