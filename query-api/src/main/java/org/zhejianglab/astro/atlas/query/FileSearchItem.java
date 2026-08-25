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
