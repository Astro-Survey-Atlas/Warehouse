package org.zhejianglab.astro.atlas.operator;

/** A Kubernetes Secret name and the keys used to satisfy plan credential references. */
public record SecretBinding(
    String secretName,
    String accessKeyKey,
    String secretKeyKey,
    String usernameKey,
    String passwordKey) {

  public boolean configured() {
    return secretName != null && !secretName.isBlank();
  }

  public String key(String credentialPart) {
    return switch (credentialPart) {
      case "accessKey" -> accessKeyKey;
      case "secretKey" -> secretKeyKey;
      case "username" -> usernameKey;
      case "password" -> passwordKey;
      default -> throw new IllegalArgumentException("unknown credential part: " + credentialPart);
    };
  }
}
