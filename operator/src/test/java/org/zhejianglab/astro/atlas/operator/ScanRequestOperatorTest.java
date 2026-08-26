package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobConditionBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import java.util.Map;
import java.util.List;
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

  @Test
  void prefersAnEquivalentRunningJobDuringOperatorMigration() {
    Job failed = job("failed", "2026-08-26T06:40:00Z", "Failed");
    Job running = job("running", "2026-08-26T06:39:00Z", "Running");

    Job selected = ScanRequestOperator.selectEquivalentJob(
        List.of(failed, running), "plan-hash", "scanner:test");

    assertEquals("running", selected.getMetadata().getName());
  }

  @Test
  void prefersAnEquivalentSuccessfulJobOverAStaleFailedDuplicate() {
    Job failed = job("failed", "2026-08-26T06:47:00Z", "Failed");
    Job succeeded = job("succeeded", "2026-08-26T06:28:00Z", "Complete");

    Job selected = ScanRequestOperator.selectEquivalentJob(
        List.of(failed, succeeded), "plan-hash", "scanner:test");

    assertEquals("succeeded", selected.getMetadata().getName());
  }

  private static Job job(String name, String creationTimestamp, String phase) {
    JobCondition condition = new JobConditionBuilder()
        .withType("Complete".equals(phase) ? "Complete" : "Failed")
        .withStatus("True")
        .build();
    JobStatus status = new JobStatus();
    if ("Running".equals(phase)) {
      status.setActive(1);
    } else {
      status.setConditions(List.of(condition));
    }
    Container scanner = new ContainerBuilder().withName("scanner").withImage("scanner:test").build();
    return new JobBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(name)
            .withCreationTimestamp(creationTimestamp)
            .withAnnotations(Map.of(OperatorConstants.PLAN_HASH_ANNOTATION, "plan-hash"))
            .build())
        .withSpec(new io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder()
            .withTemplate(new PodTemplateSpecBuilder()
                .withSpec(new PodSpecBuilder().withContainers(List.of(scanner)).build())
                .build())
            .build())
        .withStatus(status)
        .build();
  }
}
