package org.zhejianglab.astro.atlas.moc.discovery;

import java.util.Objects;

public record DiscoveryIntent(String surveyName, String releaseHint, String productHint, String policyRef) {
  public DiscoveryIntent {
    surveyName = required(surveyName, "surveyName");
    releaseHint = optional(releaseHint);
    productHint = optional(productHint);
    policyRef = optional(policyRef);
    if (policyRef == null) policyRef = "cds-public-moc-v1";
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 200) throw new IllegalArgumentException(field + " is required");
    return value.trim();
  }
  private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
