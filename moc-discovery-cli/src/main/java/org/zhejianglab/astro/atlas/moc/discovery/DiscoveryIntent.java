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

package org.zhejianglab.astro.atlas.moc.discovery;

import java.util.Objects;

public record DiscoveryIntent(String surveyName, String releaseHint, String productHint, String policyRef) {
  public DiscoveryIntent {
    surveyName = required(surveyName, "surveyName");
    releaseHint = optional(releaseHint);
    productHint = optional(productHint);
    policyRef = optional(policyRef);
    if (policyRef == null) policyRef = "cds-public-moc-v2";
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 200) throw new IllegalArgumentException(field + " is required");
    return value.trim();
  }
  private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
