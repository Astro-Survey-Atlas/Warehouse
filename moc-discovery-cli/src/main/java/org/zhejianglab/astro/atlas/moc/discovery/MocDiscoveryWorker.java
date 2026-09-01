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

package org.zhejianglab.astro.atlas.moc.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, evidence-only CDS search/probe worker. It never writes Warehouse indices. */
public final class MocDiscoveryWorker {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HttpClient client;
  private final DiscoveryPolicy policy;

  public MocDiscoveryWorker(DiscoveryPolicy policy) {
    this.policy = policy;
    this.client = HttpClient.newBuilder().connectTimeout(policy.requestTimeout()).followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  public Map<String, Object> run(DiscoveryExecutionPlan plan) {
    return run(plan, null);
  }

  public Map<String, Object> run(DiscoveryExecutionPlan plan, Path evidenceRoot) {
    long started = System.nanoTime();
    List<Map<String, Object>> requests = new ArrayList<>();
    List<Map<String, Object>> candidates = new ArrayList<>();
    long bytes = 0;
    FetchResult search = fetch(plan.searchUri(), policy.maxObjectBytes(), requests, evidenceRoot);
    bytes += number(search.record().get("bytes"));
    int recordCount = 0;
    if (Boolean.TRUE.equals(search.record().get("ok"))) {
      ExtractionResult extracted = extractCandidates(search.text(), policy, search.record());
      candidates.addAll(extracted.candidates());
      recordCount = extracted.recordCount();
    }
    // Candidate acquisition is intentionally owned by the Assets build adapter.
    // Discovery only returns the complete bounded candidate set and never turns
    // a small probe sample into the set of buildable products.
    List<Map<String, Object>> probes = new ArrayList<>();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemaVersion", 2); result.put("kind", "moc-discovery-evidence"); result.put("generatedAt", Instant.now().toString());
    result.put("policy", policy.id()); result.put("intent", Map.of("surveyName", plan.intent().surveyName(), "releaseHint", plan.intent().releaseHint() == null ? "" : plan.intent().releaseHint(), "productHint", plan.intent().productHint() == null ? "" : plan.intent().productHint()));
    result.put("search", Map.of("url", plan.searchUri().toString(), "recordCount", recordCount, "candidateCount", candidates.size())); result.put("candidates", candidates); result.put("probes", probes); result.put("probeCount", probes.size()); result.put("candidateCount", candidates.size()); result.put("requests", requests);
    result.put("limits", Map.of("maxCandidates", policy.maxCandidates(), "maxProbes", policy.maxProbes(), "maxRequests", policy.maxRequests(), "maxObjectBytes", policy.maxObjectBytes(), "maxTaskBytes", policy.maxTaskBytes(), "maxOrder", policy.maxOrder()));
    result.put("truncated", recordCount >= policy.maxCandidates()
        || bytes >= policy.maxTaskBytes()
        || elapsed(started).compareTo(policy.taskTimeout()) >= 0);
    result.put("bytes", bytes); result.put("spatialOnly", true); result.put("timeProjectionNote", "STMOC time axes are evidence-only; any spatial use must record loss of temporal information.");
    return result;
  }

  private FetchResult fetch(URI uri, long maxBytes, List<Map<String, Object>> requests, Path evidenceRoot) {
    Map<String, Object> record = new LinkedHashMap<>(); record.put("url", uri.toString());
    if (!policy.allows(uri)) { record.put("ok", false); record.put("error", "url-not-allowlisted"); requests.add(record); return new FetchResult(record, new byte[0]); }
    if (requests.size() >= policy.maxRequests()) { record.put("ok", false); record.put("error", "request-limit"); requests.add(record); return new FetchResult(record, new byte[0]); }
    byte[] body = new byte[0];
    try {
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(policy.requestTimeout()).header("Accept", "application/json,application/fits,text/plain,application/xml").GET().build();
      HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      body = readBounded(response.body(), maxBytes);
      record.put("status", response.statusCode()); record.put("bytes", body.length); record.put("ok", response.statusCode() >= 200 && response.statusCode() < 300 && body.length > 0);
      if (response.statusCode() >= 200 && response.statusCode() < 300 && body.length == 0) record.put("error", "empty-response");
      response.headers().firstValue("content-type").filter(value -> !value.isBlank()).ifPresent(value -> record.put("contentType", value));
      if (body.length > 0) {
        String hash = sha256(body);
        record.put("sha256", hash);
        if (evidenceRoot != null) record.put("evidenceRef", retainResponse(evidenceRoot, requests.size(), hash, body));
      }
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (Exception exception) { record.put("ok", false); record.put("bytes", 0); record.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage()); }
    requests.add(record);
    return new FetchResult(record, body);
  }

  private record FetchResult(Map<String, Object> record, byte[] body) {
    String text() { return new String(body, StandardCharsets.UTF_8); }
  }

  private static String retainResponse(Path evidenceRoot, int requestIndex, String hash, byte[] body) {
    try {
      Path responses = evidenceRoot.resolve("responses");
      Files.createDirectories(responses);
      String name = String.format(java.util.Locale.ROOT, "%02d-%s.bin", requestIndex + 1, hash.substring(0, 16));
      Files.write(responses.resolve(name), body);
      return "responses/" + name;
    } catch (IOException exception) {
      throw new IllegalStateException("could not retain MOC discovery response evidence", exception);
    }
  }

  private static byte[] readBounded(InputStream stream, long maxBytes) throws IOException {
    try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; long total = 0; int read;
      while ((read = input.read(buffer)) >= 0) { total += read; if (total > maxBytes) throw new IOException("response exceeds policy maxObjectBytes"); output.write(buffer, 0, read); }
      return output.toByteArray();
    }
  }

  private record ExtractionResult(List<Map<String, Object>> candidates, int recordCount) {}

  private static ExtractionResult extractCandidates(String body, DiscoveryPolicy policy, Map<String, Object> request) {
    List<Map<String, Object>> result = new ArrayList<>();
    try {
      JsonNode root = MAPPER.readTree(body);
      List<JsonNode> dataNodes = new ArrayList<>();
      if (root.isArray()) root.forEach(dataNodes::add);
      else {
        JsonNode directData = root.get("data");
        if (directData != null && directData.isArray()) directData.forEach(dataNodes::add);
        for (JsonNode nestedData : root.findValues("data")) if (nestedData.isArray() && nestedData != directData) nestedData.forEach(dataNodes::add);
      }
      Iterator<JsonNode> nodes = dataNodes.iterator();
      int recordCount = 0;
      while (nodes.hasNext() && recordCount < policy.maxCandidates()) {
        JsonNode node = nodes.next();
        if (!node.isObject()) continue;
        recordCount++;
        Map<String, Object> candidate = new LinkedHashMap<>();
        String id = first(node, "ID", "creator_did", "publisher_did", "obs_id", "id", "ivoid", "dataproduct_id");
        String recordUrl = first(node, "record_url", "recordUrl", "access_url", "accessURL", "web_access_url", "url");
        String mocUrl = first(node, "moc_access_url", "moc_url", "mocUrl", "moc", "coverage_url");
        String hipsUrl = first(node, "hips_service_url", "hipsUrl", "hips_service", "hips");
        if (mocUrl == null && id != null) mocUrl = DiscoveryPlanBuilder.spatialMocUrl(id, policy.maxOrder());
        if (recordUrl == null && id != null && (mocUrl != null || hipsUrl != null)) recordUrl = DiscoveryPlanBuilder.recordUrl(id);
        if (mocUrl == null && hipsUrl != null) mocUrl = hipsMocUrl(hipsUrl);
        candidate.put("candidateId", id == null ? "candidate-" + result.size() : id);
        if (recordUrl != null) candidate.put("recordUrl", recordUrl);
        if (mocUrl != null) candidate.put("mocUrl", mocUrl);
        if (hipsUrl != null) candidate.put("hipsUrl", hipsUrl);
        put(candidate, "title", node, "obs_title", "title", "obs_collection");
        result.add(candidate);
      }
      return new ExtractionResult(result, recordCount);
    } catch (Exception exception) {
      request.put("ok", false);
      request.put("error", "invalid-json-response");
    }
    return new ExtractionResult(result, 0);
  }

  private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
  private static String sha256(byte[] value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
  }
  private static void put(Map<String, Object> target, String targetKey, JsonNode node, String... names) { String value = first(node, names); if (value != null && !value.isBlank()) target.put(targetKey, value); }
  private static String first(JsonNode node, String... keys) { for (String key : keys) { JsonNode value = node.get(key); if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText(); } return null; }
  private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
  private static Duration elapsed(long started) { return Duration.ofNanos(System.nanoTime() - started); }

  private static String hipsMocUrl(String value) {
    String normalized = value.replaceAll("/+$", "");
    return normalized.endsWith("/Moc.fits") ? normalized : normalized + "/Moc.fits";
  }
}
