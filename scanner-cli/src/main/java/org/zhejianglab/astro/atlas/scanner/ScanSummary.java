package org.zhejianglab.astro.atlas.scanner;

import java.time.Instant;

public record ScanSummary(
    String phase,
    int discoveredFileCount,
    int processedItemCount,
    int coverageRecordCount,
    int catalogRowCount,
    int validCatalogRowCount,
    int invalidCatalogRowCount,
    int errorCount,
    Instant completedAt) {}
