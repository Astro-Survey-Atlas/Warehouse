package org.zhejianglab.astro.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CredentialResolverTest {
  @TempDir Path tempDir;

  @Test
  void resolvesMountedCredentialFilesWithoutChangingThePlan() throws Exception {
    Path access = Files.writeString(tempDir.resolve("access"), "access-value\n");
    Path secret = Files.writeString(tempDir.resolve("secret"), "secret-value\n");
    var resolved = CredentialResolver.resolve(new CredentialRef(null, null, null, null,
        access.toString(), secret.toString(), null, null));
    assertEquals("access-value", resolved.get("accessKey"));
    assertEquals("secret-value", resolved.get("secretKey"));
  }

  @Test
  void rejectsHalfConfiguredCredentialPairs() {
    assertThrows(IllegalStateException.class, () -> CredentialResolver.resolve(
        new CredentialRef(null, null, "ES_USERNAME", null, null, null, null, null)));
  }
}
