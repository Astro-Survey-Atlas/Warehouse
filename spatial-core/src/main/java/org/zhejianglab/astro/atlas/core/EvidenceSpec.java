package org.zhejianglab.astro.atlas.core;

public record EvidenceSpec(String outputPath) {
  public EvidenceSpec {
    outputPath = outputPath == null || outputPath.isBlank() ? null : outputPath.trim();
  }
}
