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

import java.util.Map;
import org.junit.jupiter.api.Test;

class MocDiscoverySummaryParserTest {
  @Test
  void parsesOnlyTheCompactCompletionMarker() {
    Map<String, Object> summary = MocDiscoverySummaryParser.parse(
        "unrelated log\nATLAS_MOC_DISCOVERY_SUMMARY {\"phase\":\"COMPLETED\",\"candidateCount\":4,\"probeCount\":2,\"truncated\":false}\n");

    assertEquals("COMPLETED", summary.get("phase"));
    assertEquals(4, summary.get("candidateCount"));
    assertEquals(2, summary.get("probeCount"));
    assertEquals(false, summary.get("truncated"));
  }

  @Test
  void ignoresMalformedOrUnmarkedLogs() {
    assertTrue(MocDiscoverySummaryParser.parse("candidateCount=4 probeCount=2").isEmpty());
    assertTrue(MocDiscoverySummaryParser.parse("ATLAS_MOC_DISCOVERY_SUMMARY {bad-json}").isEmpty());
  }
}
