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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    long started = System.nanoTime();
    List<Map<String, Object>> requests = new ArrayList<>();
    List<Map<String, Object>> candidates = new ArrayList<>();
    long bytes = 0;
    FetchResult search = fetch(plan.searchUri(), policy.maxObjectBytes(), requests);
    bytes += number(search.record().get("bytes"));
    if (Boolean.TRUE.equals(search.record().get("ok"))) candidates.addAll(extractCandidates(search.body(), policy.maxCandidates()));
    int probeCount = 0;
    List<Map<String, Object>> probes = new ArrayList<>();
    for (Map<String, Object> candidate : candidates) {
      if (probeCount >= policy.maxProbes() || requests.size() >= policy.maxRequests() || bytes >= policy.maxTaskBytes() || elapsed(started).compareTo(policy.taskTimeout()) >= 0) break;
      String candidateId = string(candidate.get("candidateId"));
      for (String key : List.of("recordUrl", "mocUrl", "hipsUrl")) {
        String value = string(candidate.get(key));
        if (value == null || value.isBlank()) continue;
        URI uri;
        try { uri = URI.create(value); } catch (IllegalArgumentException exception) { probes.add(probeError(candidateId, key, value, "invalid-url")); continue; }
        if (!policy.allows(uri)) { probes.add(probeError(candidateId, key, value, "url-not-allowlisted")); continue; }
        FetchResult response = fetch(uri, policy.maxObjectBytes(), requests);
        bytes += number(response.record().get("bytes")); probeCount++;
        Map<String, Object> probe = new LinkedHashMap<>(); probe.put("candidateId", candidateId); probe.put("kind", key); probe.put("url", uri.toString()); probe.put("status", response.record().get("status")); probe.put("bytes", response.record().get("bytes")); probe.put("ok", response.record().get("ok"));
        if (Boolean.TRUE.equals(response.record().get("ok"))) probe.put("validation", validateMoc(response.body(), uri));
        else if (response.record().get("error") != null) probe.put("error", response.record().get("error"));
        probes.add(probe);
        if (probeCount >= policy.maxProbes()) break;
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemaVersion", 1); result.put("kind", "moc-discovery-evidence"); result.put("generatedAt", Instant.now().toString());
    result.put("policy", policy.id()); result.put("intent", Map.of("surveyName", plan.intent().surveyName(), "releaseHint", plan.intent().releaseHint() == null ? "" : plan.intent().releaseHint(), "productHint", plan.intent().productHint() == null ? "" : plan.intent().productHint()));
    result.put("search", Map.of("url", plan.searchUri().toString(), "candidateCount", candidates.size())); result.put("candidates", candidates); result.put("probes", probes); result.put("probeCount", probes.size()); result.put("candidateCount", candidates.size()); result.put("requests", requests);
    result.put("limits", Map.of("maxCandidates", policy.maxCandidates(), "maxProbes", policy.maxProbes(), "maxRequests", policy.maxRequests(), "maxObjectBytes", policy.maxObjectBytes(), "maxTaskBytes", policy.maxTaskBytes(), "maxOrder", policy.maxOrder()));
    result.put("truncated", requests.size() >= policy.maxRequests() || bytes >= policy.maxTaskBytes() || elapsed(started).compareTo(policy.taskTimeout()) >= 0);
    result.put("bytes", bytes); result.put("spatialOnly", true); result.put("timeProjectionNote", "STMOC time axes are evidence-only; any spatial use must record loss of temporal information.");
    return result;
  }

  private FetchResult fetch(URI uri, long maxBytes, List<Map<String, Object>> requests) {
    Map<String, Object> record = new LinkedHashMap<>(); record.put("url", uri.toString());
    if (!policy.allows(uri)) { record.put("ok", false); record.put("error", "url-not-allowlisted"); requests.add(record); return new FetchResult(record, ""); }
    if (requests.size() >= policy.maxRequests()) { record.put("ok", false); record.put("error", "request-limit"); requests.add(record); return new FetchResult(record, ""); }
    String body = "";
    try {
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(policy.requestTimeout()).header("Accept", "application/json,application/fits,text/plain,application/xml").GET().build();
      HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes = readBounded(response.body(), maxBytes);
      body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
      record.put("status", response.statusCode()); record.put("bytes", bytes.length); record.put("ok", response.statusCode() >= 200 && response.statusCode() < 300 && bytes.length > 0);
    } catch (Exception exception) { record.put("ok", false); record.put("bytes", 0); record.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage()); }
    requests.add(record);
    return new FetchResult(record, body);
  }

  private record FetchResult(Map<String, Object> record, String body) {}

  private static byte[] readBounded(InputStream stream, long maxBytes) throws IOException {
    try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; long total = 0; int read;
      while ((read = input.read(buffer)) >= 0) { total += read; if (total > maxBytes) throw new IOException("response exceeds policy maxObjectBytes"); output.write(buffer, 0, read); }
      return output.toByteArray();
    }
  }

  private static List<Map<String, Object>> extractCandidates(String body, int limit) {
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
      while (nodes.hasNext() && result.size() < limit) { JsonNode node = nodes.next(); if (!node.isObject()) continue; Map<String, Object> candidate = new LinkedHashMap<>(); String id = first(node, "obs_id", "id", "ivoid", "dataproduct_id"); candidate.put("candidateId", id == null ? "candidate-" + result.size() : id); put(candidate, "recordUrl", node, "access_url", "accessURL", "record_url", "recordUrl", "url"); put(candidate, "mocUrl", node, "moc_url", "mocUrl", "moc", "coverage_url"); put(candidate, "hipsUrl", node, "hips_service_url", "hipsUrl", "hips_service", "hips"); put(candidate, "title", node, "obs_title", "title", "obs_collection"); if (candidate.containsKey("mocUrl") || candidate.containsKey("hipsUrl") || candidate.containsKey("recordUrl")) result.add(candidate); }
    } catch (Exception ignored) { /* raw response remains in request evidence */ }
    return result;
  }

  private static Map<String, Object> validateMoc(String body, URI uri) {
    String upper = body.toUpperCase(java.util.Locale.ROOT); Map<String, Object> result = new LinkedHashMap<>(); result.put("format", uri.getPath().toLowerCase().endsWith(".fits") || upper.contains("SIMPLE  =") ? "fits-or-fits-text" : "json-or-text"); result.put("icrs", upper.contains("ICRS") || upper.contains("EQUATORIAL")); result.put("nested", upper.contains("NUNIQ") || upper.contains("NESTED")); result.put("mocDimension", upper.contains("MOC") && !upper.contains("STMOC")); result.put("stmoc", upper.contains("STMOC") || upper.contains("TIMESYS") || upper.contains("TIME"));
    int max = -1; java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:MOCORDER|ORDER|MAXORDER)\\s*[^0-9]{0,8}(\\d+)").matcher(upper); while (matcher.find()) max = Math.max(max, Integer.parseInt(matcher.group(1))); result.put("maxOrder", max); result.put("acceptedSpatialMoc", Boolean.TRUE.equals(result.get("icrs")) && Boolean.TRUE.equals(result.get("nested")) && Boolean.TRUE.equals(result.get("mocDimension")) && max >= 0 && max <= 12); if (Boolean.TRUE.equals(result.get("stmoc"))) result.put("timeLoss", "spatial projection discards temporal axis"); return result;
  }

  private static Map<String, Object> probeError(String id, String kind, String url, String error) { return Map.of("candidateId", id, "kind", kind, "url", url, "ok", false, "error", error); }
  private static void put(Map<String, Object> target, String targetKey, JsonNode node, String... names) { String value = first(node, names); if (value != null && !value.isBlank()) target.put(targetKey, value); }
  private static String first(JsonNode node, String... keys) { for (String key : keys) { JsonNode value = node.get(key); if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText(); } return null; }
  private static String string(Object value) { return value instanceof String ? (String) value : null; }
  private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
  private static Duration elapsed(long started) { return Duration.ofNanos(System.nanoTime() - started); }
}
