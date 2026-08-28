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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceIdentityTest {
  @Test
  void canonicalizesObjectUrisAndHashesTheCanonicalValue() {
    assertEquals("s3://bucket/release/file.fits", SourceIdentity.canonicalize("S3://BUCKET/release/./file.fits?token=ignored"));
    assertEquals(SourceIdentity.sha256("s3://bucket/release/file.fits"), SourceIdentity.fileId("s3://bucket/release/file.fits"));
  }

  @Test
  void canonicalizesLocalPaths() {
    assertEquals(SourceIdentity.canonicalize(Path.of(".").toAbsolutePath()), SourceIdentity.canonicalize("./"));
  }

  @Test
  void rejectsBlankIdentity() {
    assertThrows(IllegalArgumentException.class, () -> SourceIdentity.fileId("  "));
  }
}
