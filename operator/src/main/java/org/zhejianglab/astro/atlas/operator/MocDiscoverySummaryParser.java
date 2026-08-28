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
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the compact, non-sensitive completion marker emitted by discovery Jobs. */
public final class MocDiscoverySummaryParser {
  private static final String PREFIX = "ATLAS_MOC_DISCOVERY_SUMMARY ";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MocDiscoverySummaryParser() {}

  public static Map<String, Object> parse(String log) {
    if (log == null || log.isBlank()) return Map.of();
    String[] lines = log.split("\\R");
    for (int index = lines.length - 1; index >= 0; index--) {
      String line = lines[index].trim();
      if (!line.startsWith(PREFIX)) continue;
      try {
        Map<String, Object> parsed = MAPPER.readValue(line.substring(PREFIX.length()),
            new TypeReference<Map<String, Object>>() {});
        return new LinkedHashMap<>(parsed);
      } catch (Exception ignored) {
        return Map.of();
      }
    }
    return Map.of();
  }
}
