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
    resource.setAdditionalProperty("spec", spec);

    ScanRequestSpecParser.ParsedScanRequest parsed = new ScanRequestSpecParser().parse(resource, "scanner:default");

    assertEquals("catalog-scan", parsed.name());
    assertEquals("scanner:default", parsed.spec().scanner().image());
    assertEquals("/survey", parsed.spec().plan().source().location().rootPath());
  }

  @Test
  void rejectsUnknownPlanHandlerBeforeJobCreation() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var resource = OperatorTestFixtures.request("bad-scan");
    Map<String, Object> plan = mapper.convertValue(OperatorTestFixtures.localPlan(null), Map.class);
    plan.put("handlers", java.util.List.of("default", "not-a-handler"));
    resource.setAdditionalProperty("spec", Map.of("plan", plan));

    assertThrows(OperatorValidationException.class,
        () -> new ScanRequestSpecParser().parse(resource, "scanner:default"));
  }
}
