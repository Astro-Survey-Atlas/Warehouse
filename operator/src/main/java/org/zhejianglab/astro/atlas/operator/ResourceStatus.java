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
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared status generation and idempotent status writes for namespaced resources. */
final class ResourceStatus {
  private ResourceStatus() {}

  static String generation(GenericKubernetesResource resource) {
    return resource.getMetadata().getGeneration() == null ? null
        : Long.toString(resource.getMetadata().getGeneration());
  }

  static void update(Resource<GenericKubernetesResource> resource,
      GenericKubernetesResource current, Map<String, Object> status) {
    if (same(current.get("status"), status)) return;
    resource.editStatus(item -> {
      item.setAdditionalProperty("status", status);
      return item;
    });
  }

  private static boolean same(Object existing, Map<String, Object> desired) {
    if (!(existing instanceof Map<?, ?> existingMap)) return false;
    Map<Object, Object> left = new LinkedHashMap<>();
    existingMap.forEach(left::put);
    Map<Object, Object> right = new LinkedHashMap<>(desired);
    left.remove("lastTransitionTime");
    right.remove("lastTransitionTime");
    return left.equals(right);
  }
}
