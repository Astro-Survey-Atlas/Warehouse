package org.zhejianglab.astro.atlas.scanner;

import java.time.Instant;

public record ScanSummary(
    String phase,
    int discoveredFileCount,
    int processedItemCount,
    int coverageRecordCount,
    Instant completedAt) {}
