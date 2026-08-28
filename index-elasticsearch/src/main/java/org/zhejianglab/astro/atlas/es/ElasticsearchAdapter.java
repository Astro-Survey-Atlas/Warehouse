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
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.CoverageLayer;
import org.zhejianglab.astro.atlas.core.CoverageLookup;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoveragePrecision;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.IndexContract;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.LayerState;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

/** HTTP adapter for current-state layer/file/coverage indices. */
public final class ElasticsearchAdapter implements IndexWriter, IndexReader, AutoCloseable {
  public static final int BULK_MAX_RECORDS = 100;
  public static final long BULK_MAX_BYTES = 1_500_000L;
  private static final int MAX_RETRIES = 3;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
  private final URI endpoint;
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final String authorization;

  public ElasticsearchAdapter(String endpoint, String username, String password) {
    this.endpoint = normalize(endpoint);
    mapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    if (username != null && !username.isBlank()) {
      authorization = "Basic " + Base64.getEncoder().encodeToString((username + ":" + (password == null ? "" : password))
          .getBytes(StandardCharsets.UTF_8));
    } else authorization = null;
  }

  @Override
  public boolean tryBeginLayerUpdate(CoverageLayer updatingLayer) {
    String id = updatingLayer.layerId();
    String documentPath = "/" + IndexContract.LAYER_INDEX + "/_doc/" + id;
    JsonNode root = getOptional(documentPath);
    if (root == null || !root.path("found").asBoolean()) {
      return conditionalWrite("PUT", "/" + IndexContract.LAYER_INDEX + "/_create/" + id,
          updatingLayer.toDocument());
    }
    JsonNode source = root.path("_source");
    if ("UPDATING".equalsIgnoreCase(source.path("state").asText())) {
      String expiry = optionalText(source, "lease_expires_at");
      if (expiry == null || !expired(expiry)) return false;
    }
    JsonNode sequence = root.path("_seq_no");
    JsonNode primaryTerm = root.path("_primary_term");
    if (!sequence.isNumber() || !primaryTerm.isNumber()) return false;
    return conditionalWrite("PUT", documentPath + "?if_seq_no=" + sequence.asLong()
        + "&if_primary_term=" + primaryTerm.asLong(), updatingLayer.toDocument());
  }

  @Override
  public boolean renewLayerUpdate(String layerId, String scanRunId, Instant leaseExpiresAt) {
    JsonNode root = getOptional("/" + IndexContract.LAYER_INDEX + "/_doc/" + layerId);
    if (root == null || !root.path("found").asBoolean()) return false;
    JsonNode source = root.path("_source");
    if (!"UPDATING".equalsIgnoreCase(source.path("state").asText())
        || !scanRunId.equals(source.path("scan_run_id").asText())) return false;
    String currentExpiry = optionalText(source, "lease_expires_at");
    if (currentExpiry == null || expired(currentExpiry)) return false;
    JsonNode sequence = root.path("_seq_no");
    JsonNode primaryTerm = root.path("_primary_term");
    if (!sequence.isNumber() || !primaryTerm.isNumber()) return false;
    CoverageLayer current = readLayer(source);
    CoverageLayer renewed = current.renewed(leaseExpiresAt);
    return conditionalWrite("PUT", "/" + IndexContract.LAYER_INDEX + "/_doc/" + layerId
        + "?if_seq_no=" + sequence.asLong() + "&if_primary_term=" + primaryTerm.asLong(), renewed.toDocument());
  }

  @Override
  public boolean finishLayerUpdate(CoverageLayer terminalLayer) {
    String documentPath = "/" + IndexContract.LAYER_INDEX + "/_doc/" + terminalLayer.layerId();
    JsonNode root = getOptional(documentPath);
    if (root == null || !root.path("found").asBoolean()) return false;
    JsonNode source = root.path("_source");
    if (!"UPDATING".equalsIgnoreCase(source.path("state").asText())
        || !terminalLayer.scanRunId().equals(source.path("scan_run_id").asText())) return false;
    String currentExpiry = optionalText(source, "lease_expires_at");
    if (currentExpiry == null || expired(currentExpiry)) return false;
    JsonNode sequence = root.path("_seq_no");
    JsonNode primaryTerm = root.path("_primary_term");
    if (!sequence.isNumber() || !primaryTerm.isNumber()) return false;
    return conditionalWrite("PUT", documentPath + "?if_seq_no=" + sequence.asLong()
        + "&if_primary_term=" + primaryTerm.asLong(), terminalLayer.toDocument());
  }

  @Override
  public void deleteCoverageForLayer(String layerId) {
    // Elasticsearch accepts only a boolean refresh parameter for _delete_by_query
    // (unlike the bulk API, which also supports wait_for).
    send("POST", "/" + IndexContract.COVERAGE_INDEX + "/_delete_by_query?refresh=true",
        Map.of("query", Map.of("term", Map.of("layer_id", layerId))));
  }

  @Override
  public void saveLayer(CoverageLayer layer) {
    upsertBatch(List.of(), List.of(), List.of(layer));
  }

  @Override
  public void upsertFileAsset(FileAsset fileAsset) {
    upsertBatch(List.of(fileAsset), List.of(), List.of());
  }

  @Override
  public void upsertCoverage(SpatialCoverage coverage) {
    upsertBatch(List.of(), List.of(coverage), List.of());
  }

  @Override
  public void upsertBatch(Collection<FileAsset> fileAssets, Collection<SpatialCoverage> coverages) {
    upsertBatch(fileAssets, coverages, List.of());
  }

  private void upsertBatch(Collection<FileAsset> fileAssets, Collection<SpatialCoverage> coverages,
      Collection<CoverageLayer> layers) {
    List<BulkOperation> operations = new ArrayList<>();
    if (layers != null) for (CoverageLayer layer : layers) {
      if (layer != null) operations.add(operation(IndexContract.LAYER_INDEX, layer.layerId(), layer.toDocument()));
    }
    if (fileAssets != null) for (FileAsset file : fileAssets) {
      if (file != null) operations.add(operation(IndexContract.FILE_INDEX, file.fileId(), file.toDocument()));
    }
    if (coverages != null) for (SpatialCoverage coverage : coverages) {
      if (coverage != null) operations.add(operation(IndexContract.COVERAGE_INDEX, coverage.id(), coverage.toDocument()));
    }
    sendBatches(operations);
  }

  public void installIndexTemplates() {
    putJson("/_index_template/" + ElasticsearchIndexTemplates.LAYER_TEMPLATE_NAME, ElasticsearchIndexTemplates.layerTemplate());
    putJson("/_index_template/" + ElasticsearchIndexTemplates.FILE_TEMPLATE_NAME, ElasticsearchIndexTemplates.fileTemplate());
    putJson("/_index_template/" + ElasticsearchIndexTemplates.COVERAGE_TEMPLATE_NAME, ElasticsearchIndexTemplates.coverageTemplate());
  }

  public void recreateFixedIndices() {
    for (String index : List.of(IndexContract.LAYER_INDEX, IndexContract.FILE_INDEX, IndexContract.COVERAGE_INDEX)) {
      deleteIndex(index);
      createSingleNodeIndex(index);
    }
  }

  public void verifyIndexMappings() {
    verifyMapping(IndexContract.LAYER_INDEX, ElasticsearchIndexTemplates.layerMappings());
    verifyMapping(IndexContract.FILE_INDEX, ElasticsearchIndexTemplates.fileMappings());
    verifyMapping(IndexContract.COVERAGE_INDEX, ElasticsearchIndexTemplates.coverageMappings());
  }

  @Override
  public Collection<CoverageLayer> findLayers(Collection<String> layerIds) {
    if (layerIds == null || layerIds.isEmpty()) return List.of();
    JsonNode root = send("POST", "/" + IndexContract.LAYER_INDEX + "/_mget", Map.of("ids", new ArrayList<>(layerIds)));
    List<CoverageLayer> result = new ArrayList<>();
    for (JsonNode document : root.path("docs")) if (document.path("found").asBoolean()) result.add(readLayer(document.path("_source")));
    return result;
  }

  @Override
  public Page<SpatialCoverage> searchCoverage(CoverageLookup lookup) {
    Map<String, Object> bool = new LinkedHashMap<>();
    bool.put("filter", List.of(
        Map.of("terms", Map.of("layer_id", lookup.sortedLayerIds())),
        Map.of("term", Map.of("healpix_order", lookup.order())),
        Map.of("terms", Map.of("healpix_cell", lookup.pixels().stream().sorted().toList()))));
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("query", Map.of("bool", bool));
    query.put("sort", List.of(Map.of("layer_id", "asc"), Map.of("source_file_id", "asc"),
        Map.of("healpix_cell", "asc"), Map.of("coverage_role", "asc")));
    query.put("size", lookup.limit());
    Cursor after = decodeCursor(lookup.cursor(), lookup);
    if (after != null) query.put("search_after", List.of(after.layerId(), after.fileId(), after.cell(), after.role()));
    JsonNode hits = send("POST", "/" + IndexContract.COVERAGE_INDEX + "/_search", query).path("hits").path("hits");
    List<SpatialCoverage> coverages = new ArrayList<>();
    Cursor last = null;
    for (JsonNode hit : hits) {
      JsonNode source = hit.path("_source");
      coverages.add(readCoverage(source));
      JsonNode sort = hit.path("sort");
      if (sort.isArray() && sort.size() >= 4) last = new Cursor(sort.get(0).asText(), sort.get(1).asText(), sort.get(2).asLong(), sort.get(3).asText(), lookupHash(lookup));
    }
    return new Page<>(coverages, hits.size() == lookup.limit() && last != null ? encodeCursor(last) : null);
  }

  @Override
  public Collection<FileAsset> findFiles(Collection<String> fileIds) {
    if (fileIds == null || fileIds.isEmpty()) return List.of();
    JsonNode root = send("POST", "/" + IndexContract.FILE_INDEX + "/_mget", Map.of("ids", new ArrayList<>(fileIds)));
    List<FileAsset> files = new ArrayList<>();
    for (JsonNode document : root.path("docs")) if (document.path("found").asBoolean()) files.add(readFileAsset(document.path("_source")));
    return files;
  }

  @Override
  public boolean isReady() {
    try {
      return client.send(request("GET", "/_cluster/health?wait_for_status=yellow&timeout=1s", null).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException exception) { return false; }
  }

  @Override
  public void close() {}

  private void sendBatches(List<BulkOperation> operations) {
    List<BulkOperation> batch = new ArrayList<>();
    long bytes = 0;
    for (BulkOperation operation : operations) {
      long size = operation.ndjson().getBytes(StandardCharsets.UTF_8).length;
      if (size > BULK_MAX_BYTES) throw new BulkWriteException("bulk document exceeds size limit", List.of(operation.id()));
      if (!batch.isEmpty() && (batch.size() >= BULK_MAX_RECORDS || bytes + size > BULK_MAX_BYTES)) {
        sendBulk(batch); batch = new ArrayList<>(); bytes = 0;
      }
      batch.add(operation); bytes += size;
    }
    if (!batch.isEmpty()) sendBulk(batch);
  }

  private void sendBulk(List<BulkOperation> original) {
    List<BulkOperation> pending = List.copyOf(original);
    for (int retry = 0; !pending.isEmpty(); retry++) {
      HttpResponse<String> response;
      try {
        response = client.send(request("POST", "/_bulk?refresh=wait_for", ndjson(pending)).build(), HttpResponse.BodyHandlers.ofString());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new BulkWriteException("bulk request interrupted", ids(pending), pending.size(), exception);
      } catch (IOException exception) {
        if (retry >= MAX_RETRIES) throw new BulkWriteException("bulk transport retries exhausted", ids(pending), pending.size(), exception);
        pause(retry); continue;
      }
      if (response.statusCode() / 100 != 2) {
        if (retryable(response.statusCode()) && retry < MAX_RETRIES) { pause(retry); continue; }
        throw new BulkWriteException("bulk request failed", ids(pending), pending.size());
      }
      BulkResponse result = parseBulkResponse(response.body(), pending);
      if (!result.permanentIds().isEmpty()) throw new BulkWriteException("bulk item failure", sampleIds(result.permanentIds()), result.permanentIds().size());
      if (result.retryable().isEmpty()) return;
      if (retry >= MAX_RETRIES) throw new BulkWriteException("bulk item retries exhausted", ids(result.retryable()), result.retryable().size());
      pending = List.copyOf(result.retryable()); pause(retry);
    }
  }

  private BulkResponse parseBulkResponse(String body, List<BulkOperation> operations) {
    final JsonNode root;
    try { root = mapper.readTree(body); }
    catch (IOException exception) { throw new BulkWriteException("bulk returned invalid JSON", ids(operations), operations.size(), exception); }
    JsonNode items = root == null ? null : root.get("items");
    if (items == null || !items.isArray() || items.size() != operations.size()) throw new BulkWriteException("bulk item count mismatch", ids(operations), operations.size());
    List<BulkOperation> retryable = new ArrayList<>();
    List<String> permanent = new ArrayList<>();
    for (int i = 0; i < operations.size(); i++) {
      JsonNode action = firstChild(items.get(i));
      int status = action == null ? 500 : action.path("status").asInt(500);
      if (status >= 200 && status < 300) continue;
      if (retryable(status)) retryable.add(operations.get(i)); else permanent.add(operations.get(i).id());
    }
    return new BulkResponse(retryable, permanent);
  }

  private JsonNode getOptional(String path) {
    try {
      HttpResponse<String> response = client.send(request("GET", path, null).build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404) return null;
      if (response.statusCode() >= 300) throw new IllegalStateException("Elasticsearch request failed");
      return mapper.readTree(response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("Elasticsearch request interrupted", exception);
    } catch (IOException exception) { throw new IllegalStateException("Elasticsearch request failed", exception); }
  }

  private boolean conditionalWrite(String method, String path, Map<String, Object> body) {
    try {
      HttpResponse<String> response = client.send(request(method, path, mapper.writeValueAsString(body)).build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 409) return false;
      if (response.statusCode() >= 300) throw new IllegalStateException("Elasticsearch conditional write failed with status " + response.statusCode());
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Elasticsearch conditional write interrupted", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Elasticsearch conditional write failed", exception);
    }
  }

  private void putJson(String path, Map<String, Object> body) { send("PUT", path, body); }

  private void deleteIndex(String index) {
    try {
      HttpResponse<String> response = client.send(request("DELETE", "/" + index, null).build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300 && response.statusCode() != 404) throw new IllegalStateException("index deletion failed");
    } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("index deletion interrupted", exception); }
    catch (IOException exception) { throw new IllegalStateException("index deletion failed", exception); }
  }

  private void createSingleNodeIndex(String index) { send("PUT", "/" + index, Map.of("settings", Map.of("number_of_replicas", 0))); }

  private void verifyMapping(String index, Map<String, Object> expected) {
    JsonNode actual = send("GET", "/" + index + "/_mapping", null).path(index).path("mappings");
    if (!"strict".equals(actual.path("dynamic").asText())) throw new IllegalStateException("mapping must use dynamic=strict: " + index);
    JsonNode properties = actual.path("properties");
    JsonNode expectedProperties = mapper.valueToTree(expected).path("properties");
    expectedProperties.fields().forEachRemaining(entry -> {
      JsonNode actualField = properties.path(entry.getKey());
      String expectedType = entry.getValue().path("type").asText();
      if (!actualField.isObject() || !expectedType.equals(actualField.path("type").asText())) throw new IllegalStateException("incompatible mapping field: " + index + "/" + entry.getKey());
    });
  }

  private JsonNode send(String method, String path, Map<String, Object> body) {
    try {
      String payload = body == null ? null : mapper.writeValueAsString(body);
      HttpResponse<String> response = client.send(request(method, path, payload).build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) throw new IllegalStateException("Elasticsearch request failed with status " + response.statusCode());
      return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
    } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Elasticsearch request interrupted", exception); }
    catch (IOException exception) { throw new IllegalStateException("Elasticsearch request failed", exception); }
  }

  private BulkOperation operation(String index, String id, Map<String, Object> document) {
    try {
      return new BulkOperation(index, id, mapper.writeValueAsString(Map.of("index", Map.of("_index", index, "_id", id)))
          + "\n" + mapper.writeValueAsString(document) + "\n");
    } catch (IOException exception) { throw new IllegalStateException("failed to serialize Elasticsearch document", exception); }
  }

  private SpatialCoverage readCoverage(JsonNode source) {
    return new SpatialCoverage(source.path("layer_id").asText(), source.path("source_file_id").asText(), source.path("source_uri").asText(),
        source.path("healpix_order").asInt(), source.path("healpix_cell").asLong(), CoordinateFrame.ICRS, HealpixNesting.NESTED,
        CoverageMethod.fromValue(source.path("coverage_method").asText()), CoverageRole.fromJson(source.path("coverage_role").asText()),
        Modality.of(source.path("modality").asText()), CoveragePrecision.fromJson(source.path("precision").asText()),
        !source.has("source_order") || source.get("source_order").isNull() ? null : source.path("source_order").asInt());
  }

  private CoverageLayer readLayer(JsonNode source) {
    List<Integer> orders = new ArrayList<>();
    for (JsonNode order : source.path("available_orders")) orders.add(order.asInt());
    return new CoverageLayer(source.path("layer_id").asText(), source.path("survey_id").asText(), source.path("release_id").asText(),
        source.path("product_id").asText(), Modality.of(source.path("modality").asText()), CoverageRole.fromJson(source.path("coverage_role").asText()),
        optionalText(source, "entrypoint"), LayerState.fromJson(source.path("state").asText()), source.path("scan_run_id").asText(),
        optionalInstant(source, "lease_expires_at"), optionalText(source, "source_snapshot_sha256"), orders,
        source.path("file_count").asLong(), source.path("coverage_count").asLong(), source.path("error_count").asInt(),
        optionalText(source, "error_summary"), optionalInstant(source, "updated_at"));
  }

  private FileAsset readFileAsset(JsonNode source) {
    return new FileAsset(source.path("file_id").asText(), source.path("source_uri").asText(), source.path("file_name").asText(),
        optionalText(source, "parent_uri"), FileType.valueOf(source.path("file_type").asText()),
        source.path("size_bytes").isNull() ? null : source.path("size_bytes").asLong(), optionalInstant(source, "last_modified"),
        optionalInstant(source, "indexed_at"));
  }

  private static String optionalText(JsonNode source, String field) {
    JsonNode value = source.get(field);
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }

  private static Instant optionalInstant(JsonNode source, String field) {
    String value = optionalText(source, field);
    return value == null ? null : Instant.parse(value);
  }

  private static boolean expired(String value) {
    try {
      return !Instant.parse(value).isAfter(Instant.now());
    } catch (RuntimeException exception) {
      // A malformed lease is not safe to take over.
      return false;
    }
  }

  private static String ndjson(List<BulkOperation> operations) { StringBuilder value = new StringBuilder(); operations.forEach(operation -> value.append(operation.ndjson())); return value.toString(); }
  private static List<String> ids(Collection<BulkOperation> operations) { return operations.stream().map(BulkOperation::id).limit(10).toList(); }
  private static List<String> sampleIds(Collection<String> ids) { return ids.stream().limit(10).toList(); }
  private static JsonNode firstChild(JsonNode object) { return object != null && object.isObject() && object.fields().hasNext() ? object.fields().next().getValue() : null; }
  private static boolean retryable(int status) { return status == 408 || status == 409 || status == 425 || status == 429 || status >= 500; }
  private static void pause(int retry) { try { Thread.sleep(Math.min(1000L, 50L << retry)); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("bulk retry interrupted", exception); } }

  private static String lookupHash(CoverageLookup lookup) {
    String value = lookup.sortedLayerIds() + "|" + lookup.order() + "|" + lookup.pixels().stream().sorted().toList();
    try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
  }

  private static String encodeCursor(Cursor cursor) {
    String raw = cursor.hash() + "|" + cursor.layerId() + "|" + cursor.fileId() + "|" + cursor.cell() + "|" + cursor.role();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decodeCursor(String encoded, CoverageLookup lookup) {
    if (encoded == null || encoded.isBlank()) return null;
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != 5 || !lookupHash(lookup).equals(parts[0])) throw new IllegalArgumentException();
      return new Cursor(parts[1], parts[2], Long.parseLong(parts[3]), parts[4], parts[0]);
    } catch (RuntimeException exception) { throw new IllegalArgumentException("malformed cursor"); }
  }

  private HttpRequest.Builder request(String method, String path, String body) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(REQUEST_TIMEOUT);
    request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    request.header("Content-Type", path.contains("/_bulk") ? "application/x-ndjson" : "application/json");
    if (authorization != null) request.header("Authorization", authorization);
    return request;
  }

  private static URI normalize(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("Elasticsearch endpoint is required");
    return URI.create(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
  }

  private record Cursor(String layerId, String fileId, long cell, String role, String hash) {}
  private record BulkOperation(String index, String id, String ndjson) {}
  private record BulkResponse(List<BulkOperation> retryable, List<String> permanentIds) {}

  public static final class BulkWriteException extends IllegalStateException {
    private final List<String> failedDocumentIds;
    private final int failedRecordCount;
    public BulkWriteException(String message, List<String> ids) { this(message, ids, ids.size(), null); }
    public BulkWriteException(String message, List<String> ids, int count) { this(message, ids, count, null); }
    public BulkWriteException(String message, List<String> ids, int count, Throwable cause) { super(message + ": failedRecordCount=" + count + ", failedDocumentIds=" + ids, cause); failedDocumentIds = List.copyOf(ids); failedRecordCount = count; }
    public int failedRecordCount() { return failedRecordCount; }
    public List<String> failedDocumentIds() { return failedDocumentIds; }
  }
}
