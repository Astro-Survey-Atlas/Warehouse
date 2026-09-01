/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.concurrent.CountDownLatch;

/** Dedicated discovery controller entrypoint; it never starts ScanRequest reconciliation. */
public final class MocDiscoveryMain {
  private MocDiscoveryMain() {}

  public static void main(String[] args) {
    OperatorScope scope = OperatorScope.fromEnvironment();
    MocDiscoveryConfig discoveryConfig = MocDiscoveryConfig.fromEnvironment();
    try (KubernetesClient client = new KubernetesClientBuilder().build();
         MocDiscoveryRequestOperator operator = new MocDiscoveryRequestOperator(client, scope, discoveryConfig)) {
      operator.start();
      CountDownLatch stopped = new CountDownLatch(1);
      Runtime.getRuntime().addShutdownHook(new Thread(stopped::countDown, "astro-atlas-moc-discovery-shutdown"));
      try {
        stopped.await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
