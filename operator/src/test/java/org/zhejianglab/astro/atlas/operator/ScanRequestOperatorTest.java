package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanRequestOperatorTest {
  @Test
  void terminalStatusProtectsTheSameJobButNotAChangedExecution() {
    GenericKubernetesResource resource = OperatorTestFixtures.request("scan");
    resource.setAdditionalProperty("status", Map.of("phase", "SUCCEEDED", "jobName", "scan-job-a"));

    assertTrue(ScanRequestOperator.terminalForJob(resource, "scan-job-a"));
    assertFalse(ScanRequestOperator.terminalForJob(resource, "scan-job-b"));
  }
}
