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

/** Configuration owned by the MOC discovery controller and its Jobs. */
public record MocDiscoveryConfig(String image, String evidenceClaim, String evidenceMountPath) {
  public MocDiscoveryConfig {
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("MOC discovery image must not be blank");
    }
    if (evidenceClaim == null || evidenceClaim.isBlank()) {
      throw new IllegalArgumentException("evidenceClaim must not be blank");
    }
    if (evidenceMountPath == null || evidenceMountPath.isBlank()) {
      throw new IllegalArgumentException("evidenceMountPath must not be blank");
    }
  }

  public static MocDiscoveryConfig fromEnvironment() {
    MocDiscoveryConfig defaults = defaults();
    return new MocDiscoveryConfig(
        value("MOC_DISCOVERY_IMAGE", defaults.image()),
        value("MOC_DISCOVERY_EVIDENCE_CLAIM", defaults.evidenceClaim()),
        value("MOC_DISCOVERY_EVIDENCE_MOUNT_PATH", defaults.evidenceMountPath()));
  }

  static MocDiscoveryConfig defaults() {
    return new MocDiscoveryConfig(
        "ghcr.io/astro-survey-atlas/astro-atlas-moc-discovery:0.1.0",
        "atlas-evidence", "/var/lib/atlas-evidence");
  }

  private static String value(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
