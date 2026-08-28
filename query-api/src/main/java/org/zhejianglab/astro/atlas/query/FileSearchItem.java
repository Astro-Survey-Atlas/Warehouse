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

package org.zhejianglab.astro.atlas.query;

import java.time.Instant;
import java.util.List;
import org.zhejianglab.astro.atlas.core.FileAsset;

public record FileSearchItem(
    String fileId,
    String sourceUri,
    String fileName,
    String fileType,
    Long sizeBytes,
    Instant lastModified,
    List<MatchingCoverage> matchingCoverage) {
  public FileSearchItem {
    matchingCoverage = matchingCoverage == null ? List.of() : List.copyOf(matchingCoverage);
  }

  public static FileSearchItem from(FileAsset file, List<MatchingCoverage> matchingCoverage) {
    return new FileSearchItem(
        file.fileId(),
        file.sourceUri(),
        file.fileName(),
        file.fileType().name(),
        file.sizeBytes(),
        file.lastModified(),
        matchingCoverage);
  }
}
