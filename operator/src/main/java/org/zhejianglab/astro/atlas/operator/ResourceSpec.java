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

import java.util.Map;
import java.util.TreeMap;

public record ResourceSpec(Map<String, String> requests, Map<String, String> limits) {
  public ResourceSpec {
    requests = normalize(requests);
    limits = normalize(limits);
  }

  public static ResourceSpec empty() {
    return new ResourceSpec(Map.of(), Map.of());
  }

  private static Map<String, String> normalize(Map<String, String> values) {
    return values == null ? Map.of() : Map.copyOf(new TreeMap<>(values));
  }
}
