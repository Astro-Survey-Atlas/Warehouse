package org.zhejianglab.astro.atlas.operator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ScanRequestOperator implements AutoCloseable {
  private final KubernetesClient client;
  private final OperatorConfig config;
  private final ObjectMapper mapper;
  private final ResourceDefinitionContext resourceContext;
  private final ScanRequestSpecParser parser;
  private final PlanMaterializer materializer;
  private final ScannerJobFactory jobFactory;
  private final ScheduledExecutorService executor;
  private Watch watch;

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
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "scan-request-reconciler");
      thread.setDaemon(true);
      return thread;
    });
  }

  public void start() {
    MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests =
        client.genericKubernetesResources(resourceContext);
    watch = watch(requests);
    reconcileAll(requests);
    Duration interval = config.reconcileInterval();
    long intervalMillis = Math.max(1L, interval.toMillis());
    executor.scheduleWithFixedDelay(() -> reconcileAll(requests), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    if (watch != null) watch.close();
    executor.shutdownNow();
  }

  private Watch watch(MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests) {
    Watcher<GenericKubernetesResource> watcher = new Watcher<>() {
      @Override
      public void eventReceived(Action action, GenericKubernetesResource resource) {
        reconcile(requests, resource);
      }

      @Override
      public void onClose(io.fabric8.kubernetes.client.WatcherException cause) {
        if (cause != null) System.err.println("scan request watch closed: " + cause.getClass().getSimpleName());
      }
    };
    return config.namespace().isBlank()
        ? requests.inAnyNamespace().watch(watcher)
        : requests.inNamespace(config.namespace()).watch(watcher);
  }

  private void reconcileAll(MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests) {
    try {
      List<GenericKubernetesResource> resources = config.namespace().isBlank()
          ? requests.inAnyNamespace().list().getItems()
          : requests.inNamespace(config.namespace()).list().getItems();
      for (GenericKubernetesResource resource : resources) reconcile(requests, resource);
    } catch (Exception exception) {
      System.err.println("scan request list failed: " + exception.getClass().getSimpleName());
    }
  }

  private void reconcile(
      MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests,
      GenericKubernetesResource eventResource) {
    if (eventResource == null || eventResource.getMetadata() == null
        || eventResource.getMetadata().getName() == null
        || eventResource.getMetadata().getDeletionTimestamp() != null) return;
    String namespace = eventResource.getMetadata().getNamespace();
    if (namespace == null || namespace.isBlank()) namespace = config.namespace();
    if (namespace == null || namespace.isBlank()) {
      setStatus(requests, eventResource, Map.of("phase", "INVALID", "reason", "NamespaceRequired"));
      return;
    }
    Resource<GenericKubernetesResource> resource = requests.inNamespace(namespace)
        .withName(eventResource.getMetadata().getName());
    GenericKubernetesResource current = resource.get();
    if (current == null) return;
    try {
      ScanRequestSpecParser.ParsedScanRequest parsed = parser.parse(current, config.scannerImage());
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
      if (job == null) {
        if (terminalForJob(current, jobName)) return;
        if (hasRunningLayerJob(namespace, parsed.spec().plan().layer().layerId(), jobName)) {
          updateStatus(resource, current, JobStatusMapper.status("WAITING", jobName,
              "LayerUpdateInProgress", "another non-terminal Job is refreshing this layer",
              generation(current), Map.of("layerId", parsed.spec().plan().layer().layerId())));
          return;
        }
        client.batch().v1().jobs().inNamespace(namespace)
            .resource(jobFactory.scannerJob(current, namespace, jobName, configMapName, parsed.spec(), plan)).create();
        updateStatus(resource, current, JobStatusMapper.status("SUBMITTED", jobName, null, null,
            generation(current), Map.of()));
        return;
      }
      JobStatusMapper.Observation observation = JobStatusMapper.observe(job);
      Map<String, Object> summary = terminal(observation.phase()) ? scannerSummary(namespace, job) : Map.of();
      if ("SUCCEEDED".equals(observation.phase())) {
        ScannerSummaryParser.Validation validation = ScannerSummaryParser.validateSuccessfulRun(summary,
            parsed.spec().plan().scanRunId(), parsed.spec().plan().layer().layerId());
        if (!validation.valid()) {
          updateStatus(resource, current, JobStatusMapper.status("FAILED", jobName, validation.reason(),
              "completed Job did not provide a matching scanner summary", generation(current), summary));
          return;
        }
      }
      updateStatus(resource, current, JobStatusMapper.status(observation.phase(), jobName,
          observation.reason(), observation.message(), generation(current), summary));
    } catch (OperatorValidationException exception) {
      updateStatus(resource, current, invalidStatus(current, exception.getMessage()));
    } catch (Exception exception) {
      System.err.println("scan request reconcile failed for " + current.getMetadata().getName()
          + ": " + exception.getClass().getSimpleName());
    }
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

  private static Map<String, Object> invalidStatus(GenericKubernetesResource resource, String message) {
    return JobStatusMapper.status("INVALID", null, "InvalidSpec", message, generation(resource), Map.of());
  }

  private static String generation(GenericKubernetesResource resource) {
    return resource.getMetadata().getGeneration() == null ? null
        : Long.toString(resource.getMetadata().getGeneration());
  }

  private static void updateStatus(
      Resource<GenericKubernetesResource> resource,
      GenericKubernetesResource current,
      Map<String, Object> status) {
    Object existing = current.get("status");
    if (sameStatus(existing, status)) return;
    resource.editStatus(item -> {
      item.setAdditionalProperty("status", status);
      return item;
    });
  }

  private static boolean sameStatus(Object existing, Map<String, Object> desired) {
    if (!(existing instanceof Map<?, ?> existingMap)) return false;
    Map<Object, Object> left = new java.util.LinkedHashMap<>();
    existingMap.forEach(left::put);
    Map<Object, Object> right = new java.util.LinkedHashMap<>(desired);
    left.remove("lastTransitionTime");
    right.remove("lastTransitionTime");
    return left.equals(right);
  }

  private static void setStatus(
      MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests,
      GenericKubernetesResource resource,
      Map<String, Object> status) {
    String namespace = resource.getMetadata().getNamespace();
    if (namespace == null || namespace.isBlank()) return;
    Resource<GenericKubernetesResource> handle = requests.inNamespace(namespace)
        .withName(resource.getMetadata().getName());
    updateStatus(handle, resource, status);
  }
}
