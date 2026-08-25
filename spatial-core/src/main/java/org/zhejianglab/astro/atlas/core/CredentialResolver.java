package org.zhejianglab.astro.atlas.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves credential references from environment variables or mounted files. */
public final class CredentialResolver {
  private CredentialResolver() {}

  public static Map<String, String> resolve(CredentialRef ref) {
    Map<String, String> resolved = new LinkedHashMap<>();
    if (ref == null) return resolved;
    ref.validate();
    putIfPresent(resolved, "accessKey", read(ref.accessKeyEnv(), ref.accessKeyFile(), "access key"));
    putIfPresent(resolved, "secretKey", read(ref.secretKeyEnv(), ref.secretKeyFile(), "secret key"));
    putIfPresent(resolved, "username", read(ref.usernameEnv(), ref.usernameFile(), "username"));
    putIfPresent(resolved, "password", read(ref.passwordEnv(), ref.passwordFile(), "password"));
    if (resolved.containsKey("accessKey") != resolved.containsKey("secretKey")) {
      throw new IllegalStateException("access key and secret key references must be configured together");
    }
    if (resolved.containsKey("username") != resolved.containsKey("password")) {
      throw new IllegalStateException("username and password references must be configured together");
    }
    return resolved;
  }

  private static String read(String envName, String fileName, String label) {
    if (envName != null && !envName.isBlank()) {
      String value = System.getenv(envName);
      if (value == null || value.isBlank()) throw new IllegalStateException(label + " environment reference is not set: " + envName);
      return value.trim();
    }
    if (fileName != null && !fileName.isBlank()) {
      try {
        String value = Files.readString(Path.of(fileName)).trim();
        if (value.isBlank()) throw new IllegalStateException(label + " file is empty");
        return value;
      } catch (java.io.IOException exception) {
        throw new IllegalStateException("cannot read " + label + " file", exception);
      }
    }
    return null;
  }

  private static void putIfPresent(Map<String, String> target, String key, String value) {
    if (value != null) target.put(key, value);
  }
}
