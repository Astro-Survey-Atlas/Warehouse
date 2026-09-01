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

package org.zhejianglab.astro.atlas.operator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/** Reads the compact, non-sensitive completion marker emitted by discovery Jobs. */
public final class MocDiscoverySummaryParser {
  private static final String V2_PREFIX = "ATLAS_MOC_DISCOVERY_SUMMARY_V2 ";
  private static final int MAX_PARTS = 256;
  private static final int MAX_ENCODED_BYTES = 1024 * 1024;
  private static final int MAX_JSON_BYTES = 512 * 1024;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MocDiscoverySummaryParser() {}

  public static Map<String, Object> parse(String log) {
    if (log == null || log.isBlank()) return Map.of();
    String[] lines = log.split("\\R");
    Map<String, Object> chunked = parseChunked(lines);
    if (!chunked.isEmpty()) return chunked;
    return Map.of();
  }

  private static Map<String, Object> parseChunked(String[] lines) {
    TreeMap<Integer, String> parts = new TreeMap<>();
    int expected = -1;
    for (String raw : lines) {
      String line = raw.trim();
      if (!line.startsWith(V2_PREFIX)) continue;
      String value = line.substring(V2_PREFIX.length());
      int separator = value.indexOf(' ');
      int slash = value.indexOf('/');
      if (slash <= 0 || separator <= slash + 1) return Map.of();
      try {
        int part = Integer.parseInt(value.substring(0, slash));
        int total = Integer.parseInt(value.substring(slash + 1, separator));
        if (part < 1 || total < 1 || part > total || total > MAX_PARTS) return Map.of();
        if (expected != -1 && expected != total) return Map.of();
        expected = total;
        parts.put(part, value.substring(separator + 1));
      } catch (NumberFormatException exception) {
        return Map.of();
      }
    }
    if (expected < 1 || parts.size() != expected) return Map.of();
    StringBuilder encoded = new StringBuilder();
    for (int part = 1; part <= expected; part++) {
      String value = parts.get(part);
      if (value == null || encoded.length() + value.length() > MAX_ENCODED_BYTES) return Map.of();
      encoded.append(value);
    }
    try {
      byte[] compressed = Base64.getDecoder().decode(encoded.toString());
      byte[] json;
      try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
        json = readBounded(gzip, MAX_JSON_BYTES);
      }
      Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
      return new LinkedHashMap<>(parsed);
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private static byte[] readBounded(InputStream input, int limit) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      total += read;
      if (total > limit) throw new IllegalArgumentException("MOC discovery summary exceeds the configured limit");
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }
}
