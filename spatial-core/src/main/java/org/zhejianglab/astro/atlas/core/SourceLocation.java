package org.zhejianglab.astro.atlas.core;

public record SourceLocation(String rootPath, String bucket, String prefix) {
  public static SourceLocation local(String rootPath) {
    return new SourceLocation(rootPath, null, null);
  }

  public static SourceLocation objectStore(String bucket, String prefix) {
    return new SourceLocation(null, bucket, prefix);
  }
}
