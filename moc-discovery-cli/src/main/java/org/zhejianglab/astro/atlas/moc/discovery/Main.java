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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class Main {
  private static final String SUMMARY_PREFIX = "ATLAS_MOC_DISCOVERY_SUMMARY_V2 ";
  private static final int SUMMARY_CHUNK_LENGTH = 7_000;
  private static final int SUMMARY_TEXT_LENGTH = 2_048;
  private Main() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || !"--survey-name".equals(args[0])) throw new IllegalArgumentException("usage: moc-discovery-cli --survey-name <name> [--release <hint>] [--product <hint>] [--output <path>] [--plan-only]");
    String survey = args[1]; String release = null; String product = null; Path output = null; boolean planOnly = false;
    for (int i = 2; i < args.length; i++) {
      switch (args[i]) {
        case "--release" -> release = args[++i];
        case "--product" -> product = args[++i];
        case "--output" -> output = Path.of(args[++i]);
        case "--plan-only" -> planOnly = true;
        default -> throw new IllegalArgumentException("unknown option: " + args[i]);
      }
    }
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV2();
    DiscoveryExecutionPlan plan = DiscoveryPlanBuilder.build(new DiscoveryIntent(survey, release, product, policy.id()), policy);
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 2); document.put("kind", planOnly ? "moc-discovery-execution-plan" : "moc-discovery-evidence");
    Map<String, Object> intent = new LinkedHashMap<>(); intent.put("surveyName", survey); if (release != null) intent.put("releaseHint", release); if (product != null) intent.put("productHint", product); document.put("intent", intent);
    document.put("policy", Map.of("id", policy.id(), "maxCandidates", policy.maxCandidates(), "maxProbes", policy.maxProbes(), "maxRequests", policy.maxRequests(), "maxObjectBytes", policy.maxObjectBytes(), "maxTaskBytes", policy.maxTaskBytes(), "maxOrder", policy.maxOrder()));
    document.put("searchUri", plan.searchUri().toString()); document.put("probeUris", plan.probeUris().stream().map(Object::toString).toList());
    if (!planOnly) {
      Path evidenceRoot = output == null ? null : output.toAbsolutePath().getParent();
      Map<String, Object> evidence = new MocDiscoveryWorker(policy).run(plan, evidenceRoot);
      evidence.put("executionPlan", document);
      document = evidence;
    }
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
    if (output == null) System.out.print(json); else {
      Path parent = output.toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(output, json);
      if (!planOnly) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", summaryPhase(document));
        copy(document, summary, "candidateCount");
        copy(document, summary, "probeCount");
        copy(document, summary, "bytes");
        copy(document, summary, "truncated");
        Object requests = document.get("requests");
        if (requests instanceof java.util.List<?> list) summary.put("requestCount", list.size());
        summary.put("reviewSummary", reviewSummary(document));
        printSummary(mapper, summary);
      }
    }
  }

  private static void copy(Map<String, Object> source, Map<String, Object> target, String key) {
    if (source.containsKey(key)) target.put(key, source.get(key));
  }

  private static Map<String, Object> reviewSummary(Map<String, Object> document) {
    boolean[] truncated = {false};
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemaVersion", 2);
    result.put("truncated", Boolean.TRUE.equals(document.get("truncated")));
    Object search = document.get("search");
    if (search instanceof Map<?, ?> map && map.get("recordCount") instanceof Number count) result.put("searchRecordCount", count.intValue());
    result.put("candidates", summaries(document.get("candidates"), true, truncated));
    result.put("summaryTruncated", truncated[0]);
    return result;
  }

  private static List<Map<String, Object>> summaries(Object value, boolean candidate, boolean[] truncated) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Map<String, Object>> output = new ArrayList<>();
    int limit = candidate ? DiscoveryPolicy.STATUS_CANDIDATE_LIMIT : 0;
    for (Object item : list) {
      if (candidate && output.size() >= limit) {
        truncated[0] = true;
        break;
      }
      if (!(item instanceof Map<?, ?> source)) continue;
      Map<String, Object> summary = new LinkedHashMap<>();
      for (String key : candidate
          ? List.of("candidateId", "title", "recordUrl", "mocUrl", "hipsUrl")
          : List.of("probeId", "candidateId", "kind", "url", "sha256", "evidenceRef", "contentType", "error")) {
        Object raw = source.get(key);
        if (!(raw instanceof String text) || text.isBlank()) continue;
        String bounded = (key.endsWith("Url") || "url".equals(key)) ? publicUrl(text, truncated) : bounded(text, truncated);
        if (bounded != null) summary.put(key, bounded);
      }
      for (String key : List.of("status", "bytes", "ok")) if (source.containsKey(key)) summary.put(key, source.get(key));
      Object validation = source.get("validation");
      if (validation instanceof Map<?, ?> map) summary.put("validation", validationSummary(map, truncated));
      if (!summary.isEmpty()) output.add(summary);
    }
    return output;
  }

  private static String summaryPhase(Map<String, Object> document) {
    Object requests = document.get("requests");
    if (requests instanceof List<?> list && list.stream().anyMatch(item -> item instanceof Map<?, ?> map && !Boolean.TRUE.equals(map.get("ok")))) return "FAILED";
    return "SUCCEEDED";
  }

  private static Map<String, Object> validationSummary(Map<?, ?> source, boolean[] truncated) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : List.of("format", "timeLoss")) {
      if (source.get(key) instanceof String text) result.put(key, bounded(text, truncated));
    }
    for (String key : List.of("icrs", "nested", "mocDimension", "stmoc", "acceptedSpatialMoc", "maxOrder")) {
      if (source.containsKey(key)) result.put(key, source.get(key));
    }
    return result;
  }

  private static String publicUrl(String value, boolean[] truncated) {
    try {
      URI parsed = URI.create(value);
      if (!("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
          || parsed.getHost() == null || parsed.getUserInfo() != null) return null;
      return bounded(parsed.toString(), truncated);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private static String bounded(String value, boolean[] truncated) {
    String safe = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    if (safe.length() <= SUMMARY_TEXT_LENGTH) return safe;
    truncated[0] = true;
    return safe.substring(0, SUMMARY_TEXT_LENGTH);
  }

  private static void printSummary(ObjectMapper mapper, Map<String, Object> summary) throws Exception {
    byte[] json = mapper.writeValueAsBytes(summary);
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { gzip.write(json); }
    String encoded = Base64.getEncoder().encodeToString(compressed.toByteArray());
    int parts = Math.max(1, (encoded.length() + SUMMARY_CHUNK_LENGTH - 1) / SUMMARY_CHUNK_LENGTH);
    for (int index = 0; index < parts; index++) {
      int start = index * SUMMARY_CHUNK_LENGTH;
      int end = Math.min(encoded.length(), start + SUMMARY_CHUNK_LENGTH);
      System.out.println(SUMMARY_PREFIX + (index + 1) + "/" + parts + " " + encoded.substring(start, end));
    }
  }
}
