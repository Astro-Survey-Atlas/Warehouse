/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
