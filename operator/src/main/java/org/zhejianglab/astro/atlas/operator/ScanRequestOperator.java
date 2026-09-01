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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.List;
import java.util.Map;

public final class ScanRequestOperator implements AutoCloseable {
  private final KubernetesClient client;
  private final OperatorConfig config;
  private final ObjectMapper mapper;
  private final ResourceDefinitionContext resourceContext;
  private final ScanRequestSpecParser parser;
  private final PlanMaterializer materializer;
  private final ScannerJobFactory jobFactory;
  private final NamespacedResourceController controller;

  public ScanRequestOperator(KubernetesClient client, OperatorConfig config) {
    this.client = client;
    this.config = config;
    mapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    resourceContext = new ResourceDefinitionContext.Builder()
        .withGroup(OperatorConstants.GROUP)
        .withVersion(OperatorConstants.VERSION)
        .withPlural(OperatorConstants.PLURAL)
        .withKind(OperatorConstants.KIND)
        .withNamespaced(true)
        .build();
    parser = new ScanRequestSpecParser();
    materializer = new PlanMaterializer(mapper);
    jobFactory = new ScannerJobFactory();
    controller = new NamespacedResourceController(client, config.scope(), resourceContext,
        this::reconcile, "scan-request");
  }

  public void start() {
    controller.start();
  }

  @Override
  public void close() {
    controller.close();
  }

  private synchronized void reconcile(
      MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests,
      GenericKubernetesResource eventResource) {
    if (eventResource == null || eventResource.getMetadata() == null
        || eventResource.getMetadata().getName() == null
        || eventResource.getMetadata().getDeletionTimestamp() != null) return;
    String namespace = eventResource.getMetadata().getNamespace();
    if (namespace == null || namespace.isBlank()) {
      setStatus(requests, eventResource, Map.of("phase", "INVALID", "reason", "NamespaceRequired"));
      return;
    }
    Resource<GenericKubernetesResource> resource = requests.inNamespace(namespace)
        .withName(eventResource.getMetadata().getName());
    GenericKubernetesResource current = resource.get();
    if (current == null) return;
    // ScanRequests are immutable execution records. A terminal status is only
    // retried through a new resubmission resource, never because a Job image
    // or deterministic execution hash changed during an Operator rollout.
    if (terminalStatus(current)) return;
    try {
      ScanRequestSpecParser.ParsedScanRequest parsed = parser.parse(current, config.scannerImage());
      if (!sourceVolumeReady(namespace, parsed.spec().scanner().sourceVolume(), resource, current)) return;
      RenderedPlan plan = materializer.render(parsed.spec().plan(), parsed.spec().credentials());
      String executionHash = KubeNames.sha256(plan.sha256() + "\n"
          + mapper.writeValueAsString(parsed.spec().scanner()));
      String jobName = KubeNames.scannerJobName(parsed.name(), executionHash);
      String configMapName = KubeNames.planConfigMapName(jobName);
      ConfigMap configMap = client.configMaps().inNamespace(namespace).withName(configMapName).get();
      if (configMap == null) {
        client.configMaps().inNamespace(namespace)
            .resource(jobFactory.planConfigMap(current, namespace, configMapName, jobName, plan)).create();
      }
      Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
      String observedJobName = jobName;
      Job equivalentJob = findEquivalentJob(namespace, parsed.name(), plan.sha256(), parsed.spec().scanner().image());
      if (job == null) {
        if (terminalForJob(current, jobName)) return;
        job = equivalentJob;
        if (job != null) {
          observedJobName = job.getMetadata().getName();
        }
      } else if (equivalentJob != null && !jobName.equals(equivalentJob.getMetadata().getName())) {
        job = equivalentJob;
        observedJobName = job.getMetadata().getName();
      }
      if (job == null) {
        if (hasRunningLayerJob(namespace, parsed.spec().plan().layer().layerId(), jobName)) {
          ResourceStatus.update(resource, current, JobStatusMapper.status("WAITING", jobName,
              "LayerUpdateInProgress", "another non-terminal Job is refreshing this layer",
              ResourceStatus.generation(current), Map.of("layerId", parsed.spec().plan().layer().layerId())));
          return;
        }
        client.batch().v1().jobs().inNamespace(namespace)
            .resource(jobFactory.scannerJob(current, namespace, jobName, configMapName, parsed.spec(), plan, executionHash)).create();
        ResourceStatus.update(resource, current, JobStatusMapper.status("SUBMITTED", jobName, null, null,
            ResourceStatus.generation(current), Map.of()));
        return;
      }
      JobStatusMapper.Observation observation = JobStatusMapper.observe(job);
      Map<String, Object> summary = terminal(observation.phase()) ? scannerSummary(namespace, job) : Map.of();
      if ("SUCCEEDED".equals(observation.phase())) {
        ScannerSummaryParser.Validation validation = ScannerSummaryParser.validateSuccessfulRun(summary,
            parsed.spec().plan().scanRunId(), parsed.spec().plan().layer().layerId());
        if (!validation.valid()) {
          ResourceStatus.update(resource, current, JobStatusMapper.status("FAILED", observedJobName, validation.reason(),
              "completed Job did not provide a matching scanner summary", ResourceStatus.generation(current), summary));
          return;
        }
      }
      ResourceStatus.update(resource, current, JobStatusMapper.status(observation.phase(), observedJobName,
          observation.reason(), observation.message(), ResourceStatus.generation(current), summary));
    } catch (OperatorValidationException exception) {
      ResourceStatus.update(resource, current, invalidStatus(current, exception.getMessage()));
    } catch (Exception exception) {
      System.err.println("scan request reconcile failed for " + current.getMetadata().getName()
          + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }
  }

  private boolean sourceVolumeReady(
      String namespace,
      SourceVolumeSpec source,
      Resource<GenericKubernetesResource> resource,
      GenericKubernetesResource current) {
    if (source == null) return true;
    PersistentVolumeClaim claim = client.persistentVolumeClaims().inNamespace(namespace)
        .withName(source.claimName()).get();
    if (claim == null) {
      ResourceStatus.update(resource, current, invalidStatus(current,
          "scanner.sourceVolume.claimName does not reference an existing PVC: " + source.claimName()));
      return false;
    }
    Map<String, String> labels = claim.getMetadata() == null ? null : claim.getMetadata().getLabels();
    if (labels == null || !OperatorConstants.SCANNER_SOURCE_LABEL_VALUE.equals(labels.get(OperatorConstants.SCANNER_SOURCE_LABEL))) {
      ResourceStatus.update(resource, current, invalidStatus(current,
          "source PVC is not authorized for scanner mounts: " + source.claimName()));
      return false;
    }
    String phase = claim.getStatus() == null ? null : claim.getStatus().getPhase();
    if (!"Bound".equalsIgnoreCase(phase)) {
      ResourceStatus.update(resource, current, JobStatusMapper.status("WAITING", null,
          "SourceVolumePending", "source PVC is not Bound: " + source.claimName(), ResourceStatus.generation(current), Map.of()));
      return false;
    }
    return true;
  }

  private static boolean terminal(String phase) {
    return "SUCCEEDED".equals(phase) || "FAILED".equals(phase);
  }

  static boolean terminalForJob(GenericKubernetesResource resource, String jobName) {
    Object statusValue = resource.get("status");
    if (!(statusValue instanceof Map<?, ?> status)) return false;
    Object phase = status.get("phase");
    Object recordedJob = status.get("jobName");
    return recordedJob != null && recordedJob.equals(jobName)
        && phase instanceof String phaseValue && terminal(phaseValue);
  }

  static boolean terminalStatus(GenericKubernetesResource resource) {
    Object statusValue = resource.get("status");
    if (!(statusValue instanceof Map<?, ?> status)) return false;
    Object phase = status.get("phase");
    return phase instanceof String phaseValue && terminal(phaseValue);
  }

  private Map<String, Object> scannerSummary(String namespace, Job job) {
    try {
      List<Pod> pods = client.pods().inNamespace(namespace)
          .withLabel(OperatorConstants.JOB_LABEL, job.getMetadata().getName()).list().getItems();
      if (pods == null || pods.isEmpty()) return Map.of();
      Pod summaryPod = pods.stream()
          .filter(pod -> pod.getStatus() != null && "Succeeded".equals(pod.getStatus().getPhase()))
          .findFirst().orElse(pods.get(0));
      String log = client.pods().inNamespace(namespace).withName(summaryPod.getMetadata().getName()).getLog();
      return ScannerSummaryParser.parse(log);
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private boolean hasRunningLayerJob(String namespace, String layerId, String currentJobName) {
    List<Job> jobs = client.batch().v1().jobs().inNamespace(namespace)
        .withLabel(OperatorConstants.LAYER_LABEL, KubeNames.dnsLabel(layerId, 63)).list().getItems();
    if (jobs == null) return false;
    return jobs.stream()
        .filter(candidate -> candidate.getMetadata() != null && !currentJobName.equals(candidate.getMetadata().getName()))
        .map(JobStatusMapper::observe)
        .anyMatch(observation -> !terminal(observation.phase()));
  }

  private Job findEquivalentJob(String namespace, String requestName, String planHash, String image) {
    List<Job> jobs = client.batch().v1().jobs().inNamespace(namespace)
        .withLabel(OperatorConstants.REQUEST_LABEL, KubeNames.dnsLabel(requestName, 63))
        .list().getItems();
    return selectEquivalentJob(jobs, planHash, image);
  }

  static Job selectEquivalentJob(List<Job> jobs, String planHash, String image) {
    if (jobs == null || jobs.isEmpty() || planHash == null || image == null || image.isBlank()) return null;
    Job selected = null;
    int selectedRank = Integer.MAX_VALUE;
    String selectedTimestamp = "";
    for (Job job : jobs) {
      if (!matchesExecution(job, planHash, image)) continue;
      JobStatusMapper.Observation observation = JobStatusMapper.observe(job);
      int rank = candidateRank(observation.phase());
      String timestamp = job.getMetadata() == null || job.getMetadata().getCreationTimestamp() == null
          ? "" : job.getMetadata().getCreationTimestamp();
      if (selected == null || rank < selectedRank
          || (rank == selectedRank && timestamp.compareTo(selectedTimestamp) > 0)) {
        selected = job;
        selectedRank = rank;
        selectedTimestamp = timestamp;
      }
    }
    return selected;
  }

  private static boolean matchesExecution(Job job, String planHash, String image) {
    if (job == null || job.getMetadata() == null) return false;
    Map<String, String> annotations = job.getMetadata().getAnnotations();
    if (annotations == null || !planHash.equals(annotations.get(OperatorConstants.PLAN_HASH_ANNOTATION))) {
      return false;
    }
    if (job.getSpec() == null || job.getSpec().getTemplate() == null
        || job.getSpec().getTemplate().getSpec() == null
        || job.getSpec().getTemplate().getSpec().getContainers() == null) return false;
    for (Container container : job.getSpec().getTemplate().getSpec().getContainers()) {
      if (image.equals(container.getImage())) return true;
    }
    return false;
  }

  private static int candidateRank(String phase) {
    if (!terminal(phase)) return 0;
    return "SUCCEEDED".equals(phase) ? 1 : 2;
  }

  private static Map<String, Object> invalidStatus(GenericKubernetesResource resource, String message) {
    return JobStatusMapper.status("INVALID", null, "InvalidSpec", message, ResourceStatus.generation(resource), Map.of());
  }

  private static void setStatus(
      MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests,
      GenericKubernetesResource resource,
      Map<String, Object> status) {
    String namespace = resource.getMetadata().getNamespace();
    if (namespace == null || namespace.isBlank()) return;
    Resource<GenericKubernetesResource> handle = requests.inNamespace(namespace)
        .withName(resource.getMetadata().getName());
    ResourceStatus.update(handle, resource, status);
  }
}
