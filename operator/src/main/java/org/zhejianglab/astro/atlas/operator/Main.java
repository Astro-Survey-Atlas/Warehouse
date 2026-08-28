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
