package org.zhejianglab.astro.atlas.operator;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public record OperatorConfig(
    String namespace,
    String scannerImage,
    Duration reconcileInterval) {

  public static OperatorConfig fromEnvironment() {
    return new OperatorConfig(
        value("WATCH_NAMESPACES", value("WATCH_NAMESPACE", "")),
        value("SCANNER_IMAGE", "ghcr.io/zhejianglab/astro-survey-atlas-scanner:0.1.0"),
        Duration.ofSeconds(longValue("RECONCILE_INTERVAL_SECONDS", 10L)));
  }

  public OperatorConfig {
    if (namespace == null) namespace = "";
    if (scannerImage == null || scannerImage.isBlank()) {
      throw new IllegalArgumentException("scannerImage must not be blank");
    }
    if (reconcileInterval == null || reconcileInterval.isZero() || reconcileInterval.isNegative()) {
      throw new IllegalArgumentException("reconcileInterval must be positive");
    }
  }

  /**
   * Returns the namespace scopes configured for this operator.  The legacy
   * three-argument record keeps its original shape for tests and embedders;
   * the namespace field now accepts a comma-separated list.  An empty list
   * means the Fabric8 inAnyNamespace scope.
   */
  public List<String> namespaces() {
    if (namespace == null || namespace.isBlank()) return List.of();
    return Arrays.stream(namespace.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
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
