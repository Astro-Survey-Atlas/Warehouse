package org.zhejianglab.astro.atlas.operator;

import java.nio.file.Path;

/** Describes the persistent volume mounted at the scanner's evidence root. */
public record EvidenceVolumeSpec(String claimName, String mountPath, boolean readOnly) {
  public EvidenceVolumeSpec {
    claimName = claimName == null ? null : claimName.trim();
    mountPath = mountPath == null || mountPath.isBlank()
        ? "/var/lib/atlas-evidence" : mountPath.trim();
    if (claimName == null || claimName.isBlank()) {
      throw new IllegalArgumentException("scanner.evidence.claimName is required");
    }
    Path path;
    try {
      path = Path.of(mountPath);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("scanner.evidence.mountPath is invalid", exception);
    }
    if (!path.isAbsolute() || path.normalize().toString().equals("/")) {
      throw new IllegalArgumentException("scanner.evidence.mountPath must be an absolute non-root path");
    }
  }
}
