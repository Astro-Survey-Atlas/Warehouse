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
