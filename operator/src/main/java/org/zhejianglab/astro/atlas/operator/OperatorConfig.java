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

import java.time.Duration;
import java.util.List;

public record OperatorConfig(
    String namespace,
    String scannerImage,
    Duration reconcileInterval) {

  public static OperatorConfig fromEnvironment() {
    OperatorScope scope = OperatorScope.fromEnvironment();
    return new OperatorConfig(
        scope.namespace(),
        value("SCANNER_IMAGE", "ghcr.io/zhejianglab/astro-survey-atlas-scanner:0.1.0"),
        scope.reconcileInterval());
  }

  public OperatorConfig {
    new OperatorScope(namespace, reconcileInterval);
    if (scannerImage == null || scannerImage.isBlank()) {
      throw new IllegalArgumentException("scannerImage must not be blank");
    }
  }

  public OperatorScope scope() {
    return new OperatorScope(namespace, reconcileInterval);
  }

  public List<String> namespaces() {
    return scope().namespaces();
  }

  private static String value(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
