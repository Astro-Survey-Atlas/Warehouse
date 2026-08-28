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

import org.zhejianglab.astro.atlas.core.LayerState;

/** A requested layer is known but cannot safely participate in a lookup. */
public final class LayerStateException extends RuntimeException {
  private final String layerId;
  private final LayerState state;

  public LayerStateException(String layerId, LayerState state) {
    super("layer " + layerId + " is not queryable: " + state.value());
    this.layerId = layerId;
    this.state = state;
  }

  public String layerId() {
    return layerId;
  }

  public LayerState state() {
    return state;
  }
}
