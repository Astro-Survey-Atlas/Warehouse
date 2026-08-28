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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanRequestSpecParserTest {
  @Test
  void parsesCanonicalPlanAndUsesConfiguredDefaultImage() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var resource = OperatorTestFixtures.request("catalog-scan");
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("plan", mapper.convertValue(OperatorTestFixtures.localPlan(null), Map.class));
    spec.put("scanner", Map.of("evidence", Map.of("claimName", "atlas-evidence",
        "mountPath", "/var/lib/atlas-evidence")));
    resource.setAdditionalProperty("spec", spec);

    ScanRequestSpecParser.ParsedScanRequest parsed = new ScanRequestSpecParser().parse(resource, "scanner:default");

    assertEquals("catalog-scan", parsed.name());
    assertEquals("scanner:default", parsed.spec().scanner().image());
    assertEquals("atlas-evidence", parsed.spec().scanner().evidence().claimName());
    assertEquals("/survey", parsed.spec().plan().source().location().rootPath());
  }

  @Test
  void rejectsVersionOnePlanBeforeJobCreation() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var resource = OperatorTestFixtures.request("bad-scan");
    Map<String, Object> plan = mapper.convertValue(OperatorTestFixtures.localPlan(null), Map.class);
    plan.put("version", 1);
    resource.setAdditionalProperty("spec", Map.of("plan", plan,
        "scanner", Map.of("evidence", Map.of("claimName", "atlas-evidence"))));

    assertThrows(OperatorValidationException.class,
        () -> new ScanRequestSpecParser().parse(resource, "scanner:default"));
  }

  @Test
  void rejectsPersistedPlanWithoutEvidenceVolume() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var resource = OperatorTestFixtures.request("missing-evidence-volume");
    resource.setAdditionalProperty("spec", Map.of(
        "plan", mapper.convertValue(OperatorTestFixtures.localPlan(null), Map.class)));

    OperatorValidationException exception = assertThrows(OperatorValidationException.class,
        () -> new ScanRequestSpecParser().parse(resource, "scanner:default"));

    assertEquals(true, exception.getMessage().contains("scanner.evidence is required"));
  }

  @Test
  void rejectsEvidencePathOutsideMountedRoot() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var resource = OperatorTestFixtures.request("evidence-path");
    Map<String, Object> plan = mapper.convertValue(OperatorTestFixtures.localPlan(null), Map.class);
    ((Map<String, Object>) plan.get("evidence")).put("outputPath", "/tmp/not-evidence");
    resource.setAdditionalProperty("spec", Map.of(
        "plan", plan,
        "scanner", Map.of("evidence", Map.of("claimName", "atlas-evidence",
            "mountPath", "/var/lib/atlas-evidence"))));

    OperatorValidationException exception = assertThrows(OperatorValidationException.class,
        () -> new ScanRequestSpecParser().parse(resource, "scanner:default"));

    assertEquals(true, exception.getMessage().contains("under scanner.evidence.mountPath"));
  }

  @Test
  void rejectsReadOnlyEvidenceVolumeForPersistedScan() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var resource = OperatorTestFixtures.request("readonly-evidence");
    resource.setAdditionalProperty("spec", Map.of(
        "plan", mapper.convertValue(OperatorTestFixtures.localPlan(null), Map.class),
        "scanner", Map.of("evidence", Map.of("claimName", "atlas-evidence",
            "mountPath", "/var/lib/atlas-evidence", "readOnly", true))));

    OperatorValidationException exception = assertThrows(OperatorValidationException.class,
        () -> new ScanRequestSpecParser().parse(resource, "scanner:default"));

    assertEquals(true, exception.getMessage().contains("readOnly must be false"));
  }
}
