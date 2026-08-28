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
