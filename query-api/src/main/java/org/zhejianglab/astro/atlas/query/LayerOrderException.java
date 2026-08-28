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

package org.zhejianglab.astro.atlas.query;

/** A requested layer is active but does not publish the requested HEALPix order. */
public final class LayerOrderException extends RuntimeException {
  private final String layerId;
  private final int order;

  public LayerOrderException(String layerId, int order) {
    super("layer " + layerId + " does not publish HEALPix order " + order);
    this.layerId = layerId;
    this.order = order;
  }

  public String layerId() {
    return layerId;
  }

  public int order() {
    return order;
  }
}
