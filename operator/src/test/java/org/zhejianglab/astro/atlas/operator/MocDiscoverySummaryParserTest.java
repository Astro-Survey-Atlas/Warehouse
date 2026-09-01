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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class MocDiscoverySummaryParserTest {
  @Test
  void parsesOnlyTheCompactCompletionMarker() {
    Map<String, Object> summary = MocDiscoverySummaryParser.parse(
        "unrelated log\nATLAS_MOC_DISCOVERY_SUMMARY_V2 1/1 e30=\n");

    assertTrue(summary.isEmpty(), "an invalid compressed marker must be ignored");
  }

  @Test
  void ignoresMalformedOrUnmarkedLogs() {
    assertTrue(MocDiscoverySummaryParser.parse("candidateCount=4 probeCount=2").isEmpty());
    assertTrue(MocDiscoverySummaryParser.parse("ATLAS_MOC_DISCOVERY_SUMMARY {bad-json}").isEmpty());
  }

  @Test
  void reconstructsChunkedReviewSummaryWithoutPuttingBodiesInLogs() throws Exception {
    Map<String, Object> expected = Map.of(
        "phase", "SUCCEEDED",
        "candidateCount", 1,
        "reviewSummary", Map.of(
            "schemaVersion", 2,
            "truncated", false,
            "summaryTruncated", false,
            "candidates", List.of(Map.of("candidateId", "jwst-moc", "title", "JWST"))));
    byte[] json = new ObjectMapper().writeValueAsBytes(expected);
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { gzip.write(json); }
    String encoded = Base64.getEncoder().encodeToString(compressed.toByteArray());
    int split = encoded.length() / 2;
    String log = "ATLAS_MOC_DISCOVERY_SUMMARY_V2 1/2 " + encoded.substring(0, split) + "\n"
        + "ATLAS_MOC_DISCOVERY_SUMMARY_V2 2/2 " + encoded.substring(split) + "\n";

    Map<String, Object> parsed = MocDiscoverySummaryParser.parse(log);

    assertEquals(1, parsed.get("candidateCount"));
    assertEquals(expected.get("reviewSummary"), parsed.get("reviewSummary"));
    assertTrue(log.getBytes(StandardCharsets.UTF_8).length < 8 * 1024);
  }

  @Test
  void rejectsTheRetiredV1MarkerEvenWhenItsJsonIsValid() {
    assertTrue(MocDiscoverySummaryParser.parse(
        "ATLAS_MOC_DISCOVERY_SUMMARY {\"phase\":\"SUCCEEDED\",\"candidateCount\":1}").isEmpty());
  }
}
