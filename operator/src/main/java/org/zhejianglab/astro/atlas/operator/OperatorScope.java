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
import java.util.Arrays;
import java.util.List;

/** Namespace and scheduling settings shared by the namespaced controllers. */
public record OperatorScope(String namespace, Duration reconcileInterval) {
  public OperatorScope {
    if (namespace == null || namespace.isBlank() || parseNamespaces(namespace).isEmpty()) {
      throw new IllegalArgumentException("WATCH_NAMESPACES must contain at least one namespace");
    }
    if (reconcileInterval == null || reconcileInterval.isZero() || reconcileInterval.isNegative()) {
      throw new IllegalArgumentException("reconcileInterval must be positive");
    }
  }

  public static OperatorScope fromEnvironment() {
    return new OperatorScope(
        value("WATCH_NAMESPACES", value("WATCH_NAMESPACE", "")),
        Duration.ofSeconds(longValue("RECONCILE_INTERVAL_SECONDS", 10L)));
  }

  public List<String> namespaces() {
    return parseNamespaces(namespace);
  }

  private static List<String> parseNamespaces(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(namespace -> !namespace.isBlank())
        .distinct()
        .toList();
  }

  private static String value(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static long longValue(String name, long fallback) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) return fallback;
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) throw new IllegalArgumentException(name + " must be positive");
      return parsed;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(name + " must be an integer", exception);
    }
  }
}
