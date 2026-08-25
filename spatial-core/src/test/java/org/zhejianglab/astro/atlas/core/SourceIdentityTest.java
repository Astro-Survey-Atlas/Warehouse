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
