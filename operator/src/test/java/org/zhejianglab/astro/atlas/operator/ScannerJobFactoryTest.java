package org.zhejianglab.astro.atlas.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerJobFactoryTest {
  @Test
  void createsDeterministicJobAndPlanConfigMapWithOwnerReference() {
    GenericKubernetesResource request = OperatorTestFixtures.request("nightly-scan");
    request.getMetadata().setLabels(Map.of(
        OperatorConstants.TRACKING_LABEL_PREFIX + "caller", "assets",
        "untrusted.example/owner", "ignored"));
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var plan = new PlanMaterializer(mapper).render(
        OperatorTestFixtures.localPlan(null), CredentialsSpec.empty());
    var spec = new ScanRequestSpec(OperatorTestFixtures.localPlan(null),
        new ScannerSpec("atlas-scanner:test", null, 1, 86_400L, 86_400,
            ResourceSpec.empty(), OperatorTestFixtures.evidenceVolume()), CredentialsSpec.empty());
    ScannerJobFactory factory = new ScannerJobFactory();
    String jobName = KubeNames.scannerJobName("nightly-scan", plan.sha256());
    String configMapName = KubeNames.planConfigMapName(jobName);

    Job job = factory.scannerJob(request, "atlas", jobName, configMapName, spec, plan, "execution-hash");

    assertEquals(jobName, job.getMetadata().getName());
    assertEquals("atlas-scanner:test", job.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    assertEquals(List.of("java", "-jar", "/app/scanner-cli.jar"),
        job.getSpec().getTemplate().getSpec().getContainers().get(0).getCommand());
    assertEquals(120L, job.getSpec().getTemplate().getSpec().getTerminationGracePeriodSeconds());
    assertEquals(2, job.getSpec().getTemplate().getSpec().getVolumes().size());
    assertEquals("atlas-evidence", job.getSpec().getTemplate().getSpec().getVolumes().get(1)
        .getPersistentVolumeClaim().getClaimName());
    assertEquals("uid-1", job.getMetadata().getOwnerReferences().get(0).getUid());
    assertTrue(job.getMetadata().getAnnotations().containsKey(OperatorConstants.PLAN_HASH_ANNOTATION));
    assertEquals("execution-hash",
        job.getMetadata().getAnnotations().get(OperatorConstants.EXECUTION_HASH_ANNOTATION));
    assertEquals("assets", job.getMetadata().getLabels().get(OperatorConstants.TRACKING_LABEL_PREFIX + "caller"));
    assertEquals("assets", job.getSpec().getTemplate().getMetadata().getLabels()
        .get(OperatorConstants.TRACKING_LABEL_PREFIX + "caller"));
    assertEquals(OperatorConstants.OPERATOR_NAME,
        job.getMetadata().getLabels().get(OperatorConstants.MANAGED_BY_LABEL));
    assertTrue(!job.getMetadata().getLabels().containsKey("untrusted.example/owner"));
    assertEquals(Map.of("plan.json", plan.json()),
        factory.planConfigMap(request, "atlas", configMapName, jobName, plan).getData());
  }

  @Test
  void keepsPlanHashInJobNameForLongRequestNames() {
    String requestName = "a-very-long-scan-request-name-that-needs-truncation-before-the-hash";
    String first = KubeNames.scannerJobName(requestName, "1234567890abcdef");
    String second = KubeNames.scannerJobName(requestName, "abcdef1234567890");

    assertTrue(first.length() <= 63);
    assertTrue(second.length() <= 63);
    org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
  }
}
