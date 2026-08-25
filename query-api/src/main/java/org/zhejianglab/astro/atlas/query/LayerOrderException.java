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
