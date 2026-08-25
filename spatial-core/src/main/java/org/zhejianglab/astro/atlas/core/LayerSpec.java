package org.zhejianglab.astro.atlas.core;

import java.util.regex.Pattern;

public record LayerSpec(
    String layerId,
    String surveyId,
    String releaseId,
    String productId,
    Modality modality,
    CoverageRole coverageRole,
    String entrypoint) {
  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]*");

  public LayerSpec {
    requireId(layerId, "layerId");
    requireId(surveyId, "surveyId");
    requireText(releaseId, "releaseId");
    requireText(productId, "productId");
    if (modality == null) throw new IllegalArgumentException("layer.modality is required");
    if (coverageRole == null) throw new IllegalArgumentException("layer.coverageRole is required");
    entrypoint = normalize(entrypoint);
  }

  private static void requireId(String value, String field) {
    if (value == null || !ID.matcher(value).matches()) {
      throw new IllegalArgumentException("layer." + field + " must be a lowercase ID");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("layer." + field + " is required");
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
