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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Thin watcher translating intent-only discovery requests into immutable evidence Jobs. */
public final class MocDiscoveryRequestOperator implements AutoCloseable {
  private static final String KIND = "MocDiscoveryRequest";
  private static final String PLURAL = "mocdiscoveryrequests";
  private final KubernetesClient client;
  private final OperatorConfig config;
  private final ObjectMapper mapper = new ObjectMapper();
  private final ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
      .withGroup(OperatorConstants.GROUP).withVersion(OperatorConstants.VERSION).withPlural(PLURAL).withKind(KIND).withNamespaced(true).build();
  private final ScheduledExecutorService executor;
  private final List<Watch> watches = new ArrayList<>();

  public MocDiscoveryRequestOperator(KubernetesClient client, OperatorConfig config) {
    this.client = client; this.config = config;
    executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "moc-discovery-reconciler"); t.setDaemon(true); return t; });
  }

  public void start() {
    MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests = client.genericKubernetesResources(context);
    Watcher<GenericKubernetesResource> watcher = new Watcher<>() {
      @Override public void eventReceived(Action action, GenericKubernetesResource resource) { reconcile(requests, resource); }
      @Override public void onClose(io.fabric8.kubernetes.client.WatcherException cause) { if (cause != null) System.err.println("MOC discovery watch closed: " + cause.getMessage()); }
    };
    config.namespaces().forEach(namespace -> watches.add(requests.inNamespace(namespace).watch(watcher)));
    reconcileAll(requests);
    long interval = Math.max(1, config.reconcileInterval().toMillis());
    executor.scheduleWithFixedDelay(() -> reconcileAll(requests), interval, interval, TimeUnit.MILLISECONDS);
  }

  @Override public void close() { watches.forEach(Watch::close); watches.clear(); executor.shutdownNow(); }

  private void reconcileAll(MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests) {
    try {
      for (String namespace : config.namespaces()) {
        List<GenericKubernetesResource> resources = requests.inNamespace(namespace).list().getItems();
        if (resources != null) resources.forEach(resource -> reconcile(requests, resource));
      }
    } catch (Exception exception) { System.err.println("MOC discovery list failed: " + exception.getClass().getSimpleName()); }
  }

  private synchronized void reconcile(MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests, GenericKubernetesResource event) {
    if (event == null || event.getMetadata() == null || event.getMetadata().getName() == null || event.getMetadata().getDeletionTimestamp() != null) return;
    String namespace = event.getMetadata().getNamespace(); if (namespace == null || namespace.isBlank()) return;
    Resource<GenericKubernetesResource> resource = requests.inNamespace(namespace).withName(event.getMetadata().getName());
    GenericKubernetesResource current = resource.get(); if (current == null) return;
    try {
      JsonNode spec = mapper.valueToTree(current.get("spec"));
      String surveyName = spec.path("query").path("surveyName").asText("").trim();
      String policyRef = spec.path("policyRef").asText("").trim();
      if (surveyName.isBlank() || !"cds-public-moc-v1".equals(policyRef)) { setStatus(resource, Map.of("phase", "INVALID", "reason", "InvalidIntent")); return; }
      String jobName = KubeNames.dnsLabel(event.getMetadata().getName() + "-moc-discovery", 63);
      String evidencePath = evidencePath(jobName);
      Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
      if (job == null) {
        client.batch().v1().jobs().inNamespace(namespace).resource(job(event, namespace, jobName, surveyName, spec)).create();
        setStatus(resource, Map.of("phase", "SUBMITTED", "jobName", jobName, "evidencePath", evidencePath));
      } else {
        JobStatusMapper.Observation observation = JobStatusMapper.observe(job);
        Map<String, Object> summary = terminal(observation.phase()) ? discoverySummary(namespace, job) : Map.of();
        Map<String, Object> status = new LinkedHashMap<>(JobStatusMapper.status(observation.phase(), jobName,
            observation.reason(), observation.message(), generation(current), summary));
        copyCount(status, summary, "candidateCount");
        copyCount(status, summary, "probeCount");
        status.put("evidencePath", evidencePath);
        setStatus(resource, status);
      }
    } catch (Exception exception) { setStatus(resource, Map.of("phase", "FAILED", "reason", "ReconcileError", "message", exception.getMessage() == null ? "discovery reconcile failed" : exception.getMessage())); }
  }

  Job job(GenericKubernetesResource request, String namespace, String name, String surveyName, JsonNode spec) {
    String image = env("MOC_DISCOVERY_IMAGE", "ghcr.io/zhejianglab/astro-survey-atlas-moc-discovery:0.1.0");
    String claim = env("MOC_DISCOVERY_EVIDENCE_CLAIM", "atlas-evidence");
    String mount = env("MOC_DISCOVERY_EVIDENCE_MOUNT_PATH", "/var/lib/atlas-evidence");
    String output = mount.replaceAll("/+$", "") + "/moc-discovery/" + name;
    var args = new java.util.ArrayList<String>(); args.add("--survey-name"); args.add(surveyName);
    if (spec.path("query").hasNonNull("releaseHint")) { args.add("--release"); args.add(spec.path("query").path("releaseHint").asText()); }
    if (spec.path("query").hasNonNull("productHint")) { args.add("--product"); args.add(spec.path("query").path("productHint").asText()); }
    args.add("--output"); args.add(output + "/execution-plan.json");
    Map<String, String> labels = new LinkedHashMap<>(); labels.put(OperatorConstants.MANAGED_BY_LABEL, OperatorConstants.OPERATOR_NAME); labels.put("atlas.zhejianglab.org/moc-discovery", "true"); labels.put("atlas.zhejianglab.org/request", KubeNames.dnsLabel(request.getMetadata().getName(), 63));
    var container = new ContainerBuilder().withName("moc-discovery").withImage(image).withImagePullPolicy("IfNotPresent")
        .withCommand("java", "-jar", "/app/moc-discovery-cli.jar").withArgs(args)
        .withVolumeMounts(new VolumeMountBuilder().withName("evidence").withMountPath(mount).build()).build();
    var pod = new PodSpecBuilder().withRestartPolicy("Never").withContainers(container)
        .withVolumes(new VolumeBuilder().withName("evidence").withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder().withClaimName(claim).withReadOnly(false).build()).build()).build();
    var template = new PodTemplateSpecBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build()).withSpec(pod).build();
    var jobSpec = new JobSpecBuilder().withBackoffLimit(1).withActiveDeadlineSeconds(600L).withTtlSecondsAfterFinished(86400).withTemplate(template).build();
    ObjectMetaBuilder metadata = new ObjectMetaBuilder().withName(name).withNamespace(namespace).withLabels(labels);
    if (request.getMetadata().getUid() != null) {
      metadata.withOwnerReferences(new OwnerReferenceBuilder()
          .withApiVersion(OperatorConstants.API_VERSION)
          .withKind(KIND)
          .withName(request.getMetadata().getName())
          .withUid(request.getMetadata().getUid())
          .withController(true)
          .withBlockOwnerDeletion(true)
          .build());
    }
    return new JobBuilder().withApiVersion("batch/v1").withKind("Job").withMetadata(metadata.build()).withSpec(jobSpec).build();
  }

  private Map<String, Object> discoverySummary(String namespace, Job job) {
    try {
      List<Pod> pods = client.pods().inNamespace(namespace)
          .withLabel(OperatorConstants.JOB_LABEL, job.getMetadata().getName()).list().getItems();
      if (pods == null || pods.isEmpty()) return Map.of();
      Pod summaryPod = pods.stream()
          .filter(pod -> pod.getStatus() != null && "Succeeded".equals(pod.getStatus().getPhase()))
          .findFirst().orElse(pods.get(0));
      String log = client.pods().inNamespace(namespace).withName(summaryPod.getMetadata().getName()).getLog();
      return MocDiscoverySummaryParser.parse(log);
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private static void copyCount(Map<String, Object> status, Map<String, Object> summary, String key) {
    Object value = summary.get(key);
    if (value instanceof Number number && number.longValue() >= 0) status.put(key, number.intValue());
  }

  private static boolean terminal(String phase) {
    return "SUCCEEDED".equals(phase) || "FAILED".equals(phase);
  }

  private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
  private String evidencePath(String jobName) { return env("MOC_DISCOVERY_EVIDENCE_MOUNT_PATH", "/var/lib/atlas-evidence").replaceAll("/+$", "") + "/moc-discovery/" + jobName + "/execution-plan.json"; }
  private static String generation(GenericKubernetesResource resource) { return resource.getMetadata().getGeneration() == null ? null : Long.toString(resource.getMetadata().getGeneration()); }
  private static void setStatus(Resource<GenericKubernetesResource> resource, Map<String, Object> status) { resource.editStatus(item -> { item.setAdditionalProperty("status", status); return item; }); }
}
