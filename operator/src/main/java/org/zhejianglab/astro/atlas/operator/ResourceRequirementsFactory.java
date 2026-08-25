package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import java.util.Map;

public final class ResourceRequirementsFactory {
  private ResourceRequirementsFactory() {}

  public static ResourceRequirements create(ResourceSpec spec) {
    ResourceRequirementsBuilder builder = new ResourceRequirementsBuilder();
    add(builder, spec.requests(), true);
    add(builder, spec.limits(), false);
    return builder.build();
  }

  private static void add(ResourceRequirementsBuilder builder, Map<String, String> values, boolean requests) {
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
        throw new OperatorValidationException(java.util.List.of("resource keys and quantities must not be blank"));
      }
      try {
        if (requests) builder.addToRequests(entry.getKey(), new Quantity(entry.getValue()));
        else builder.addToLimits(entry.getKey(), new Quantity(entry.getValue()));
      } catch (IllegalArgumentException exception) {
        throw new OperatorValidationException(java.util.List.of(
            "invalid resource quantity for " + entry.getKey()));
      }
    }
  }
}
