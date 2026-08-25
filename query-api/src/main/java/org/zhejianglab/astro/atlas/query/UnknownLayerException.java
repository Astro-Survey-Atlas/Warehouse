package org.zhejianglab.astro.atlas.query;

/** The caller requested a layer ID that is not present in the current index. */
public final class UnknownLayerException extends RuntimeException {
  private final String layerId;

  public UnknownLayerException(String layerId) {
    super("unknown coverage layer: " + layerId);
    this.layerId = layerId;
  }

  public String layerId() {
    return layerId;
  }
}
