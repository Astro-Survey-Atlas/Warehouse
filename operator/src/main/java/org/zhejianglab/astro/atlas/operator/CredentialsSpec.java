package org.zhejianglab.astro.atlas.operator;

public record CredentialsSpec(SecretBinding source, SecretBinding sink) {
  public static CredentialsSpec empty() {
    return new CredentialsSpec(null, null);
  }
}
