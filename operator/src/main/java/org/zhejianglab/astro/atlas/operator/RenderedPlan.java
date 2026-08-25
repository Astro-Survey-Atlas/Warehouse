package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;

public record RenderedPlan(
    String json,
    String sha256,
    List<EnvVar> environment,
    List<Volume> volumes,
    List<VolumeMount> volumeMounts) {
  public RenderedPlan {
    environment = List.copyOf(environment);
    volumes = List.copyOf(volumes);
    volumeMounts = List.copyOf(volumeMounts);
  }
}
