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

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Shared namespaced watch/list/schedule lifecycle for thin resource controllers. */
final class NamespacedResourceController implements AutoCloseable {
  @FunctionalInterface
  interface Reconciler {
    void reconcile(
        MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList,
            Resource<GenericKubernetesResource>> requests,
        GenericKubernetesResource resource);
  }

  private final KubernetesClient client;
  private final OperatorScope scope;
  private final ResourceDefinitionContext context;
  private final Reconciler reconciler;
  private final String resourceName;
  private final ScheduledExecutorService executor;
  private final List<Watch> watches = new ArrayList<>();

  NamespacedResourceController(KubernetesClient client, OperatorScope scope,
      ResourceDefinitionContext context, Reconciler reconciler,
      String resourceName) {
    this.client = client;
    this.scope = scope;
    this.context = context;
    this.reconciler = reconciler;
    this.resourceName = resourceName;
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, resourceName + "-reconciler");
      thread.setDaemon(true);
      return thread;
    });
  }

  void start() {
    MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> requests =
        client.genericKubernetesResources(context);
    Watcher<GenericKubernetesResource> watcher = new Watcher<>() {
      @Override
      public void eventReceived(Action action, GenericKubernetesResource resource) {
        reconciler.reconcile(requests, resource);
      }

      @Override
      public void onClose(io.fabric8.kubernetes.client.WatcherException cause) {
        if (cause != null) {
          System.err.println(resourceName + " watch closed: "
              + cause.getClass().getSimpleName());
        }
      }
    };
    scope.namespaces().forEach(namespace -> watches.add(requests.inNamespace(namespace).watch(watcher)));
    reconcileAll(requests);
    long intervalMillis = Math.max(1L, scope.reconcileInterval().toMillis());
    executor.scheduleWithFixedDelay(() -> reconcileAll(requests), intervalMillis,
        intervalMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    watches.forEach(Watch::close);
    watches.clear();
    executor.shutdownNow();
  }

  private void reconcileAll(
      MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList,
          Resource<GenericKubernetesResource>> requests) {
    try {
      for (String namespace : scope.namespaces()) {
        List<GenericKubernetesResource> resources = requests.inNamespace(namespace).list().getItems();
        if (resources != null) resources.forEach(resource -> reconciler.reconcile(requests, resource));
      }
    } catch (Exception exception) {
      System.err.println(resourceName + " list failed: " + exception.getClass().getSimpleName());
    }
  }
}
