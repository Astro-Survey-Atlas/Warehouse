package org.zhejianglab.astro.atlas.operator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerSummaryParserTest {
  @Test
  void extractsOnlyStructuredSummaryFields() {
    var summary = ScannerSummaryParser.parse("secret-looking diagnostic\n"
        + "phase=COMPLETED scanRunId=run-1 layerId=layer-1 snapshot=abc123 discovered=6 processed=6 coverage=48 "
        + "catalogRows=0 catalogValid=0 catalogInvalid=0 errors=0 orders=[8] evidence=/var/lib/evidence\n");

    assertEquals("COMPLETED", summary.get("phase"));
    assertEquals(6, summary.get("discoveredFileCount"));
    assertEquals(48, summary.get("coverageRecordCount"));
    assertEquals("layer-1", summary.get("layerId"));
    assertEquals(java.util.List.of(8), summary.get("availableOrders"));
    assertTrue(ScannerSummaryParser.parse("no summary").isEmpty());
  }

  @Test
  void requiresMatchingSuccessfulScannerTruth() {
    var summary = ScannerSummaryParser.parse(
        "phase=COMPLETED scanRunId=run-1 layerId=layer-1 snapshot=abc123 discovered=1 processed=1 coverage=1 "
            + "catalogRows=0 catalogValid=0 catalogInvalid=0 errors=0 orders=[8] evidence=/var/lib/evidence");

    assertTrue(ScannerSummaryParser.validateSuccessfulRun(summary, "run-1", "layer-1").valid());
    assertEquals("ScannerSummaryIdentityMismatch",
        ScannerSummaryParser.validateSuccessfulRun(summary, "run-2", "layer-1").reason());
    assertEquals("ScannerSummaryMissing",
        ScannerSummaryParser.validateSuccessfulRun(java.util.Map.of(), "run-1", "layer-1").reason());
  }
}
