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

public record ScannerSpec(
    String image,
    String serviceAccountName,
    Integer backoffLimit,
    Long activeDeadlineSeconds,
    Integer ttlSecondsAfterFinished,
    ResourceSpec resources,
    EvidenceVolumeSpec evidence,
    SourceVolumeSpec sourceVolume) {

  public ScannerSpec(
      String image,
      String serviceAccountName,
      Integer backoffLimit,
      Long activeDeadlineSeconds,
      Integer ttlSecondsAfterFinished,
      ResourceSpec resources) {
    this(image, serviceAccountName, backoffLimit, activeDeadlineSeconds, ttlSecondsAfterFinished, resources, null, null);
  }

  public ScannerSpec(
      String image,
      String serviceAccountName,
      Integer backoffLimit,
      Long activeDeadlineSeconds,
      Integer ttlSecondsAfterFinished,
      ResourceSpec resources,
      EvidenceVolumeSpec evidence) {
    this(image, serviceAccountName, backoffLimit, activeDeadlineSeconds, ttlSecondsAfterFinished, resources, evidence, null);
  }

  public ScannerSpec {
    if (resources == null) resources = ResourceSpec.empty();
    if (backoffLimit != null && backoffLimit < 0) {
      throw new IllegalArgumentException("scanner.backoffLimit must not be negative");
    }
    if (activeDeadlineSeconds != null && activeDeadlineSeconds <= 0) {
      throw new IllegalArgumentException("scanner.activeDeadlineSeconds must be positive");
    }
    if (ttlSecondsAfterFinished != null && ttlSecondsAfterFinished < 0) {
      throw new IllegalArgumentException("scanner.ttlSecondsAfterFinished must not be negative");
    }
  }

  public static ScannerSpec defaults(String image) {
    return new ScannerSpec(image, null, 1, 86_400L, 86_400, ResourceSpec.empty(), null, null);
  }
}
