package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanRequestOperatorTest {
  @Test
  void terminalStatusProtectsTheSameJobButNotAChangedExecution() {
    GenericKubernetesResource resource = OperatorTestFixtures.request("scan");
    resource.setAdditionalProperty("status", Map.of("phase", "SUCCEEDED", "jobName", "scan-job-a"));

    assertTrue(ScanRequestOperator.terminalForJob(resource, "scan-job-a"));
    assertFalse(ScanRequestOperator.terminalForJob(resource, "scan-job-b"));
  }

  @Test
  void aCompletedJobWithoutMatchingSummaryCannotBeReportedAsSuccess() {
    var validation = ScannerSummaryParser.validateSuccessfulRun(Map.of(
        "phase", "COMPLETED", "scanRunId", "other-run", "layerId", "local-layer", "errorCount", 0,
        "sourceSnapshotSha256", "snapshot"), "local-run-20260825", "local-layer");

    assertFalse(validation.valid());
    assertEquals("ScannerSummaryIdentityMismatch", validation.reason());
  }
}
