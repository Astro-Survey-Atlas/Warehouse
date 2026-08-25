package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JobStatusMapper {
  private JobStatusMapper() {}

  public static Observation observe(Job job) {
    JobStatus status = job.getStatus();
    if (status == null) return new Observation("SUBMITTED", null, null);
    JobCondition complete = condition(status, "Complete");
    if (isTrue(complete)) return new Observation("SUCCEEDED", complete.getReason(), complete.getMessage());
    JobCondition failed = condition(status, "Failed");
    if (isTrue(failed)) return new Observation("FAILED", failed.getReason(), failed.getMessage());
    if (status.getSucceeded() != null && status.getSucceeded() > 0) {
      return new Observation("SUCCEEDED", null, null);
    }
    if (status.getActive() != null && status.getActive() > 0) {
      return new Observation("RUNNING", null, null);
    }
    if (status.getFailed() != null && status.getFailed() > 0) {
      return new Observation("FAILED", null, null);
    }
    return new Observation("SUBMITTED", null, null);
  }

  public static Map<String, Object> status(
      String phase,
      String jobName,
      String reason,
      String message,
      String observedGeneration,
      Map<String, Object> summary) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("phase", phase);
    if (jobName != null) result.put("jobName", jobName);
    if (reason != null && !reason.isBlank()) result.put("reason", safe(reason));
    if (message != null && !message.isBlank()) result.put("message", safe(message));
    if (observedGeneration != null) result.put("observedGeneration", observedGeneration);
    if (summary != null && !summary.isEmpty()) result.put("summary", summary);
    result.put("lastTransitionTime", java.time.Instant.now().toString());
    return result;
  }

  private static JobCondition condition(JobStatus status, String type) {
    if (status.getConditions() == null) return null;
    return status.getConditions().stream().filter(item -> type.equals(item.getType())).findFirst().orElse(null);
  }

  private static boolean isTrue(JobCondition condition) {
    return condition != null && "True".equalsIgnoreCase(condition.getStatus());
  }

  private static String safe(String value) {
    return value.length() > 500 ? value.substring(0, 500) : value;
  }

  public record Observation(String phase, String reason, String message) {}
}
