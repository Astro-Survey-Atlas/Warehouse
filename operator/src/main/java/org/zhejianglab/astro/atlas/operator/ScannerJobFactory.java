package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScannerJobFactory {
  public ConfigMap planConfigMap(
      GenericKubernetesResource request,
      String namespace,
      String configMapName,
      String jobName,
      RenderedPlan plan) {
    return new ConfigMapBuilder()
        .withApiVersion("v1")
        .withKind("ConfigMap")
        .withMetadata(metadata(request, namespace, configMapName, jobName, plan.sha256(), null))
        .withImmutable(true)
        .withData(Map.of("plan.json", plan.json()))
        .build();
  }

  public Job scannerJob(
      GenericKubernetesResource request,
      String namespace,
      String jobName,
      String configMapName,
      ScanRequestSpec spec,
      RenderedPlan plan) {
    ScannerSpec scanner = spec.scanner();
    List<Volume> volumes = new ArrayList<>(plan.volumes());
    volumes.add(new VolumeBuilder().withName("scan-plan")
        .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName).build()).build());
    List<VolumeMount> mounts = new ArrayList<>(plan.volumeMounts());
    mounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder().withName("scan-plan")
        .withMountPath("/etc/atlas/scan").withReadOnly(true).build());
    EvidenceVolumeSpec evidence = scanner.evidence();
    if (evidence != null) {
      volumes.add(new VolumeBuilder().withName("scan-evidence")
          .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
              .withClaimName(evidence.claimName()).withReadOnly(evidence.readOnly()).build()).build());
      mounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder().withName("scan-evidence")
          .withMountPath(evidence.mountPath()).withReadOnly(evidence.readOnly()).build());
    }

    Container container = new ContainerBuilder()
        .withName("scanner")
        .withImage(scanner.image())
        .withImagePullPolicy("IfNotPresent")
        .withCommand("java", "-jar", "/app/scanner-cli.jar")
        .withArgs("--plan", OperatorConstants.PLAN_PATH)
        .withEnv(plan.environment())
        .withResources(ResourceRequirementsFactory.create(scanner.resources()))
        .withVolumeMounts(mounts)
        .build();

    return new JobBuilder()
        .withApiVersion("batch/v1")
        .withKind("Job")
        .withMetadata(metadata(request, namespace, jobName, jobName, plan.sha256(), spec.plan().layer().layerId()))
        .withSpec(new JobSpecBuilder()
            .withBackoffLimit(valueOr(scanner.backoffLimit(), 1))
            .withActiveDeadlineSeconds(valueOr(scanner.activeDeadlineSeconds(), 86_400L))
            .withTtlSecondsAfterFinished(valueOr(scanner.ttlSecondsAfterFinished(), 86_400))
            .withTemplate(new PodTemplateSpecBuilder()
                .withMetadata(new ObjectMetaBuilder().withLabels(labels(request, jobName, spec.plan().layer().layerId())).build())
                .withSpec(new PodSpecBuilder()
                    .withRestartPolicy("Never")
                    .withServiceAccountName(scanner.serviceAccountName())
                    .withContainers(container)
                    .withVolumes(volumes)
                    .build())
                .build())
            .build())
        .build();
  }

  private static io.fabric8.kubernetes.api.model.ObjectMeta metadata(
      GenericKubernetesResource request,
      String namespace,
      String name,
      String jobName,
      String planHash,
      String layerId) {
    ObjectMetaBuilder builder = new ObjectMetaBuilder()
        .withName(name)
        .withNamespace(namespace)
        .withLabels(labels(request, jobName, layerId))
        .addToAnnotations(OperatorConstants.PLAN_HASH_ANNOTATION, planHash);
    if (request.getMetadata().getUid() != null) {
      builder.withOwnerReferences(new OwnerReferenceBuilder()
          .withApiVersion(OperatorConstants.API_VERSION)
          .withKind(OperatorConstants.KIND)
          .withName(request.getMetadata().getName())
          .withUid(request.getMetadata().getUid())
          .withController(true)
          .withBlockOwnerDeletion(true)
          .build());
    }
    return builder.build();
  }

  private static Map<String, String> labels(GenericKubernetesResource request, String jobName, String layerId) {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put(OperatorConstants.MANAGED_BY_LABEL, OperatorConstants.OPERATOR_NAME);
    labels.put(OperatorConstants.REQUEST_LABEL, KubeNames.dnsLabel(request.getMetadata().getName(), 63));
    labels.put("job-name", KubeNames.dnsLabel(jobName, 63));
    if (layerId != null && !layerId.isBlank()) labels.put(OperatorConstants.LAYER_LABEL, KubeNames.dnsLabel(layerId, 63));
    return labels;
  }

  private static int valueOr(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static long valueOr(Long value, long fallback) {
    return value == null ? fallback : value;
  }
}
