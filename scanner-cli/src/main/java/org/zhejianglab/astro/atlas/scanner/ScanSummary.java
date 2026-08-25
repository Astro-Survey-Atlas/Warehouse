package org.zhejianglab.astro.atlas.scanner;

import java.time.Instant;
import java.util.List;

public record ScanSummary(
    String phase,
    String scanRunId,
    String layerId,
    String sourceSnapshotSha256,
    int discoveredFileCount,
    int processedItemCount,
    int coverageRecordCount,
    int catalogRowCount,
    int validCatalogRowCount,
    int invalidCatalogRowCount,
    int errorCount,
    List<Integer> availableOrders,
    String evidencePath,
    Instant completedAt) {
  public ScanSummary {
    availableOrders = availableOrders == null ? List.of() : List.copyOf(availableOrders);
  }
}
