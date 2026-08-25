package org.zhejianglab.astro.atlas.operator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerSummaryParserTest {
  @Test
  void extractsOnlyStructuredSummaryFields() {
    var summary = ScannerSummaryParser.parse("secret-looking diagnostic\n"
        + "phase=COMPLETED discovered=6 processed=6 coverage=48 catalogRows=0 "
        + "catalogValid=0 catalogInvalid=0 errors=0\n");

    assertEquals("COMPLETED", summary.get("phase"));
    assertEquals(6, summary.get("discoveredFileCount"));
    assertEquals(48, summary.get("coverageRecordCount"));
    assertTrue(ScannerSummaryParser.parse("no summary").isEmpty());
  }
}
