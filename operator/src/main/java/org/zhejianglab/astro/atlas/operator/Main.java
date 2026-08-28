package org.zhejianglab.astro.atlas.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.concurrent.CountDownLatch;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    OperatorConfig config = OperatorConfig.fromEnvironment();
    try (KubernetesClient client = new KubernetesClientBuilder().build();
         ScanRequestOperator operator = new ScanRequestOperator(client, config);
         MocDiscoveryRequestOperator mocDiscovery = new MocDiscoveryRequestOperator(client, config)) {
      operator.start();
      mocDiscovery.start();
      CountDownLatch stopped = new CountDownLatch(1);
      Runtime.getRuntime().addShutdownHook(new Thread(stopped::countDown, "astro-atlas-operator-shutdown"));
      try {
        stopped.await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
