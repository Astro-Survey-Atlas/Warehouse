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
