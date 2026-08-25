package org.zhejianglab.astro.atlas.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zhejianglab.astro.atlas.core.CredentialRef;
import org.zhejianglab.astro.atlas.core.Filters;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SinkConnector;
import org.zhejianglab.astro.atlas.core.SinkSpec;
import org.zhejianglab.astro.atlas.core.SinkType;
import org.zhejianglab.astro.atlas.core.SourceConnector;
import org.zhejianglab.astro.atlas.core.SourceLocation;
import org.zhejianglab.astro.atlas.core.SourceSpec;
import org.zhejianglab.astro.atlas.core.SourceType;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

class LocalScanTest {
  @TempDir Path tempDir;

  @Test
  void scansSingleCatalogPathAndProducesDeduplicatedCoverage() throws Exception {
    Path catalog = tempDir.resolve("single.csv");
    Files.writeString(catalog, "ra,dec\n150.3115318540502,1.7296315530453528\n150.3115318540502,1.7296315530453528\n", StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();
    ScanSummary summary = new ScanService(new LocalSourceAdapter(), index).scan(plan(catalog));
    assertEquals(1, summary.discoveredFileCount());
    assertEquals(1, summary.processedItemCount());
    assertEquals(1, summary.coverageRecordCount());
    assertEquals(1, index.files().size());
    assertEquals(1, index.coverages().size());
    assertEquals(catalog.getParent().toUri().toString(), index.files().get(0).parentUri());
  }

  @Test
  void scansFitsAndCatalogAndDeduplicatesCatalogCells() throws Exception {
    writeFits(tempDir.resolve("image.fits"));
    Files.writeString(tempDir.resolve("catalog.csv"), "ra,dec\n180.25,-2.5\n180.25,-2.5\n", StandardCharsets.UTF_8);
    Files.writeString(tempDir.resolve("notes.txt"), "ignored", StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();
    ScanSummary summary = new ScanService(new LocalSourceAdapter(), index).scan(plan());

    assertEquals(2, summary.discoveredFileCount());
    assertEquals(2, summary.processedItemCount());
    assertEquals(2, summary.coverageRecordCount());
    assertEquals(2, index.files().size());
    assertEquals(2, index.coverages().size());
    assertTrue(index.files().stream().allMatch(file -> file.spatialStatus() == SpatialStatus.KNOWN));
  }

  private ScanPlan plan() {
    return plan(tempDir);
  }

  private ScanPlan plan(Path root) {
    return new ScanPlan(1,
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local(root.toString())),
        new Filters(List.of(".fits", ".csv"), List.of()), List.of("default", "fits", "catalog", "coverage"),
        Modality.of("image"), new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())));
  }

  private static void writeFits(Path path) throws Exception {
    String[] cards = {
        card("SIMPLE  =                    T"),
        card("NAXIS   =                    2"),
        card("CRVAL1  =             180.25"),
        card("CRVAL2  =               -2.5"),
        card("END")
    };
    StringBuilder header = new StringBuilder();
    for (String card : cards) header.append(card);
    while (header.length() % 2880 != 0) header.append(' ');
    Files.writeString(path, header, StandardCharsets.US_ASCII);
  }

  private static String card(String value) {
    return String.format("%-80s", value);
  }
}
