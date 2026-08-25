package org.zhejianglab.astro.atlas.operator;

public record ScannerSpec(
    String image,
    String serviceAccountName,
    Integer backoffLimit,
    Long activeDeadlineSeconds,
    Integer ttlSecondsAfterFinished,
    ResourceSpec resources) {

  public ScannerSpec {
    if (resources == null) resources = ResourceSpec.empty();
    if (backoffLimit != null && backoffLimit < 0) {
      throw new IllegalArgumentException("scanner.backoffLimit must not be negative");
    }
    if (activeDeadlineSeconds != null && activeDeadlineSeconds <= 0) {
      throw new IllegalArgumentException("scanner.activeDeadlineSeconds must be positive");
    }
    if (ttlSecondsAfterFinished != null && ttlSecondsAfterFinished < 0) {
      throw new IllegalArgumentException("scanner.ttlSecondsAfterFinished must not be negative");
    }
  }

  public static ScannerSpec defaults(String image) {
    return new ScannerSpec(image, null, 1, 86_400L, 86_400, ResourceSpec.empty());
  }
}
