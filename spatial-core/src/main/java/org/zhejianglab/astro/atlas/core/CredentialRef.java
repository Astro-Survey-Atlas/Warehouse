package org.zhejianglab.astro.atlas.core;

/** Names of environment variables or mounted files, never credential values. */
public record CredentialRef(
    String accessKeyEnv,
    String secretKeyEnv,
    String usernameEnv,
    String passwordEnv,
    String accessKeyFile,
    String secretKeyFile,
    String usernameFile,
    String passwordFile) {
  public static CredentialRef none() {
    return new CredentialRef(null, null, null, null, null, null, null, null);
  }

  public void validate() {
    checkReference(accessKeyEnv, "accessKeyEnv");
    checkReference(secretKeyEnv, "secretKeyEnv");
    checkReference(usernameEnv, "usernameEnv");
    checkReference(passwordEnv, "passwordEnv");
    checkReference(accessKeyFile, "accessKeyFile");
    checkReference(secretKeyFile, "secretKeyFile");
    checkReference(usernameFile, "usernameFile");
    checkReference(passwordFile, "passwordFile");
  }

  private static void checkReference(String value, String name) {
    if (value != null && value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
  }
}
