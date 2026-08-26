package org.zhejianglab.astro.atlas.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zhejianglab.astro.atlas.core.CredentialRef;
import org.zhejianglab.astro.atlas.core.CatalogSpec;
import org.zhejianglab.astro.atlas.core.CoveragePrecision;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.ExtractionMode;
import org.zhejianglab.astro.atlas.core.ExtractionSpec;
import org.zhejianglab.astro.atlas.core.EvidenceSpec;
import org.zhejianglab.astro.atlas.core.Filters;
import org.zhejianglab.astro.atlas.core.LayerSpec;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SinkConnector;
import org.zhejianglab.astro.atlas.core.SinkSpec;
import org.zhejianglab.astro.atlas.core.SinkType;
import org.zhejianglab.astro.atlas.core.SourceConnector;
import org.zhejianglab.astro.atlas.core.SourceLocation;
import org.zhejianglab.astro.atlas.core.SourceSpec;
import org.zhejianglab.astro.atlas.core.SourceType;

class LocalScanTest {
  @TempDir Path tempDir;

  @Test
  void scansSingleCatalogPathAndProducesDeduplicatedCoverage() throws Exception {
    Path catalog = tempDir.resolve("single.csv");
    Files.writeString(catalog, "ra,dec\n150.3115318540502,1.7296315530453528\n150.3115318540502,1.7296315530453528\n", StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();
    ScanSummary summary = new ScanService(new LocalSourceAdapter(), index).scan(catalogPlan(catalog));
    assertEquals(1, summary.discoveredFileCount());
    assertEquals(1, summary.processedItemCount());
    assertEquals(1, summary.coverageRecordCount());
    assertEquals(1, index.files().size());
    assertEquals(1, index.coverages().size());
    assertEquals(catalog.getParent().toUri().toString(), index.files().get(0).parentUri());
    assertEquals(CoveragePrecision.EXACT, index.coverages().get(0).precision());
    Path evidence = catalog.getParent().resolve("evidence-catalog-layer");
    assertTrue(Files.isRegularFile(evidence.resolve("source-inventory.json")));
    assertTrue(Files.isRegularFile(evidence.resolve("source-inventory.json.gz")));
    assertTrue(Files.isRegularFile(evidence.resolve("normalized-scan.json")));
    assertTrue(Files.isRegularFile(evidence.resolve("errors.json")));
  }

  @Test
  void scansFitsAndCatalogAndDeduplicatesCatalogCells() throws Exception {
    writeFits(tempDir.resolve("image.fits"));
    Files.writeString(tempDir.resolve("catalog.csv"), "ra,dec\n180.25,-2.5\n180.25,-2.5\n", StandardCharsets.UTF_8);
    Files.writeString(tempDir.resolve("notes.txt"), "ignored", StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();
    ScanSummary fitsSummary = new ScanService(new LocalSourceAdapter(), index).scan(fitsPlan(tempDir));
    ScanSummary catalogSummary = new ScanService(new LocalSourceAdapter(), index).scan(catalogPlan(tempDir));

    assertEquals(1, fitsSummary.discoveredFileCount());
    assertEquals(1, catalogSummary.discoveredFileCount());
    assertEquals(2, index.files().size());
    assertEquals(2, index.coverages().size());
    assertTrue(index.coverages().stream().anyMatch(coverage -> coverage.coverageMethod().value().equals("fits_header_position")));
    assertTrue(index.coverages().stream().anyMatch(coverage -> coverage.coverageMethod().value().equals("catalog_radec")));
  }

  @Test
  void derivesMultipleCoverageCellsFromImageWcsFootprint() throws Exception {
    writeWcsFits(tempDir.resolve("wcs.fits"));
    InMemoryIndex index = new InMemoryIndex();

    ScanSummary summary = new ScanService(new LocalSourceAdapter(), index).scan(wcsPlan(tempDir));

    assertEquals(1, summary.discoveredFileCount());
    assertTrue(summary.coverageRecordCount() > 1);
    assertTrue(index.coverages().stream().allMatch(coverage -> coverage.precision() == CoveragePrecision.ESTIMATED));
  }

  @Test
  void refreshReplacesOnlyTheCurrentLayerCoverage() throws Exception {
    Path catalog = tempDir.resolve("refresh.csv");
    Files.writeString(catalog, "ra,dec\n180.25,-2.5\n181.25,-2.5\n", StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();
    ScanPlan plan = catalogPlan(catalog);
    ScanSummary first = new ScanService(new LocalSourceAdapter(), index).scan(plan);
    assertEquals(2, first.coverageRecordCount());
    Files.writeString(catalog, "ra,dec\n180.25,-2.5\n", StandardCharsets.UTF_8);
    ScanSummary second = new ScanService(new LocalSourceAdapter(), index).scan(plan);
    assertEquals(1, second.coverageRecordCount());
    assertEquals(1, index.coverages().size());
    assertEquals(org.zhejianglab.astro.atlas.core.LayerState.ACTIVE, index.layers().get(0).state());
  }

  @Test
  void parsesQuotedCatalogRecordsWithConfiguredColumnsAndReportsInvalidRows() throws Exception {
    Path catalog = tempDir.resolve("quoted.csv");
    Files.writeString(catalog,
        "source label,sky ra,sky dec\n"
            + "\"source, one\",180.25,-2.5\n"
            + "\"source\n two\",180.25,-2.5\n"
            + "bad,not-a-number,-2.5\n",
        StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();

    assertThrows(IllegalStateException.class, () -> new ScanService(new LocalSourceAdapter(), index).scan(
        catalogPlan(catalog, new CatalogSpec("sky ra", "sky dec", null, null, null))));

    assertEquals(org.zhejianglab.astro.atlas.core.LayerState.FAILED, index.layers().get(0).state());
    assertTrue(Files.readString(tempDir.resolve("evidence-catalog-layer/summary.json")).contains("\"catalogRowCount\":3"));
    assertTrue(Files.readString(tempDir.resolve("evidence-catalog-layer/summary.json")).contains("\"invalidCatalogRowCount\":1"));
  }

  @Test
  void parsesWhitespaceSeparatedCatCatalogsWithCommentedHeaders() throws Exception {
    Path catalog = tempDir.resolve("csst.cat");
    Files.writeString(catalog,
        "# obj_ID ra dec class\n"
            + "source-1 180.25 -2.5 star\n"
            + "source-2 180.25 -2.5 galaxy\n",
        StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();

    ScanSummary summary = new ScanService(new LocalSourceAdapter(), index).scan(
        catalogPlan(catalog, new CatalogSpec("ra", "dec", null, null, null), List.of(".cat")));

    assertEquals(2, summary.catalogRowCount());
    assertEquals(2, summary.validCatalogRowCount());
    assertEquals(1, summary.coverageRecordCount());
    assertEquals(org.zhejianglab.astro.atlas.core.FileType.CATALOG, index.files().get(0).fileType());
  }

  @Test
  void marksAValidPartialScanAsFailedAndHidesItsCoverage() throws Exception {
    writeFits(tempDir.resolve("valid.fits"));
    Files.writeString(tempDir.resolve("invalid.fits"),
        card("SIMPLE  =                    T") + card("NAXIS   =                    2") + card("END"),
        StandardCharsets.US_ASCII);
    InMemoryIndex index = new InMemoryIndex();

    assertThrows(IllegalStateException.class,
        () -> new ScanService(new LocalSourceAdapter(), index).scan(fitsPlan(tempDir)));
    assertEquals(org.zhejianglab.astro.atlas.core.LayerState.FAILED, index.layers().get(0).state());
    assertEquals(1, index.coverages().size(), "partial edges may remain physically present");
    assertEquals(0, index.searchCoverage(new org.zhejianglab.astro.atlas.core.CoverageLookup(
        java.util.Set.of("fits-layer"), 8,
        java.util.Set.of(index.coverages().get(0).healpixCell()), 100, null)).items().size());
  }

  @Test
  void rejectsCatalogWithMissingConfiguredColumnsEvenWhenItHasNoRows() throws Exception {
    Path catalog = tempDir.resolve("missing-columns.csv");
    Files.writeString(catalog, "object_id,magnitude\n", StandardCharsets.UTF_8);
    InMemoryIndex index = new InMemoryIndex();

    assertThrows(IllegalStateException.class,
        () -> new ScanService(new LocalSourceAdapter(), index).scan(catalogPlan(catalog)));
    assertEquals(org.zhejianglab.astro.atlas.core.LayerState.FAILED, index.layers().get(0).state());
    assertEquals(1, index.layers().get(0).errorCount());
  }

  @Test
  void marksAZeroCoverageScanWithExtractionErrorsAsFailed() throws Exception {
    Path fits = tempDir.resolve("missing-spatial-header.fits");
    String[] cards = {
        card("SIMPLE  =                    T"),
        card("NAXIS   =                    2"),
        card("END")
    };
    Files.write(fits, String.join("", cards).getBytes(StandardCharsets.US_ASCII));
    InMemoryIndex index = new InMemoryIndex();

    assertThrows(IllegalStateException.class, () -> new ScanService(new LocalSourceAdapter(), index).scan(fitsPlan(fits)));
    assertEquals(org.zhejianglab.astro.atlas.core.LayerState.FAILED, index.layers().get(0).state());
    assertEquals(0, index.coverages().size());
    assertEquals(1, index.layers().get(0).errorCount());
  }

  @Test
  void writesFailedEvidenceWhenEnumerationFailsBeforeTheFirstItem() throws Exception {
    InMemoryIndex index = new InMemoryIndex();
    SourceAdapter source = new SourceAdapter() {
      @Override
      public Stream<org.zhejianglab.astro.atlas.core.InputItem> enumerate(ScanPlan plan) {
        throw new IllegalStateException("listing failed");
      }

      @Override
      public org.zhejianglab.astro.atlas.core.SourceContent open(org.zhejianglab.astro.atlas.core.InputItem item) {
        throw new UnsupportedOperationException();
      }
    };

    assertThrows(IllegalStateException.class, () -> new ScanService(source, index).scan(catalogPlan(tempDir)));
    Path evidence = tempDir.resolve("evidence-catalog-layer");
    assertTrue(Files.readString(evidence.resolve("summary.json")).contains("\"phase\":\"FAILED\""));
    assertTrue(Files.readString(evidence.resolve("summary.json")).contains("\"errorCount\":1"));
    assertTrue(Files.readString(evidence.resolve("errors.json")).contains("listing failed"));
    assertTrue(Files.readString(evidence.resolve("normalized-scan.json")).contains("\"phase\":\"FAILED\""));
  }

  @Test
  void duplicateRunCannotBypassAnUnexpiredLeaseAndExpiredLeaseCanBeTakenOver() {
    InMemoryIndex index = new InMemoryIndex();
    org.zhejianglab.astro.atlas.core.LayerSpec layer =
        new org.zhejianglab.astro.atlas.core.LayerSpec("lease-layer", "survey", "release", "product",
            Modality.IMAGE, CoverageRole.FOOTPRINT, null);
    org.zhejianglab.astro.atlas.core.CoverageLayer first =
        org.zhejianglab.astro.atlas.core.CoverageLayer.updating(layer, "run-a", Instant.now().plusSeconds(60));
    assertTrue(index.tryBeginLayerUpdate(first));
    assertTrue(!index.tryBeginLayerUpdate(
        org.zhejianglab.astro.atlas.core.CoverageLayer.updating(layer, "run-a", Instant.now().plusSeconds(60))));
    assertTrue(!index.tryBeginLayerUpdate(
        org.zhejianglab.astro.atlas.core.CoverageLayer.updating(layer, "run-b", Instant.now().plusSeconds(60))));
    org.zhejianglab.astro.atlas.core.CoverageLayer renewed = first.renewed(Instant.now().plusSeconds(120));
    assertTrue(index.renewLayerUpdate("lease-layer", "run-a", renewed.leaseExpiresAt()));
    assertTrue(index.finishLayerUpdate(renewed.failed("failed", "snapshot", 1)));

    InMemoryIndex takeover = new InMemoryIndex();
    assertTrue(takeover.tryBeginLayerUpdate(
        org.zhejianglab.astro.atlas.core.CoverageLayer.updating(layer, "old-run", Instant.now().minusSeconds(1))));
    assertTrue(takeover.tryBeginLayerUpdate(
        org.zhejianglab.astro.atlas.core.CoverageLayer.updating(layer, "new-run", Instant.now().plusSeconds(60))));
  }

  private ScanPlan catalogPlan(Path root) {
    return catalogPlan(root, new CatalogSpec("ra", "dec", null, null, null), List.of(".csv"));
  }

  private ScanPlan catalogPlan(Path root, CatalogSpec catalog) {
    return catalogPlan(root, catalog, List.of(".csv"));
  }

  private ScanPlan catalogPlan(Path root, CatalogSpec catalog, List<String> suffixes) {
    return plan(root, "catalog-layer", Modality.CATALOG, CoverageRole.OCCUPANCY,
        ExtractionMode.CATALOG_RADEC, catalog, suffixes);
  }

  private ScanPlan fitsPlan(Path root) {
    return plan(root, "fits-layer", Modality.IMAGE, CoverageRole.FOOTPRINT,
        ExtractionMode.FITS_HEADER_POSITION, CatalogSpec.empty(), List.of(".fits"));
  }

  private ScanPlan wcsPlan(Path root) {
    return plan(root, "wcs-layer", Modality.IMAGE, CoverageRole.FOOTPRINT,
        ExtractionMode.FITS_WCS, CatalogSpec.empty(), List.of(".fits"));
  }

  private ScanPlan plan(Path root, String layerId, Modality modality, CoverageRole role,
      ExtractionMode mode, CatalogSpec catalog, List<String> suffixes) {
    Path evidenceRoot = Files.isRegularFile(root) ? root.getParent() : root;
    return new ScanPlan(2, layerId + "-run",
        new LayerSpec(layerId, "test-survey", "local", layerId, modality, role, null),
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local(root.toString())),
        new Filters(suffixes, List.of()), new ExtractionSpec(mode, 8, catalog),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())),
        new EvidenceSpec(evidenceRoot.resolve("evidence-" + layerId).toString()));
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

  private static void writeWcsFits(Path path) throws Exception {
    String[] cards = {
        card("SIMPLE  =                    T"),
        card("BITPIX  =                  -32"),
        card("NAXIS   =                    2"),
        card("NAXIS1  =                  100"),
        card("NAXIS2  =                  100"),
        card("CTYPE1  =           'RA---TAN'"),
        card("CTYPE2  =          'DEC--TAN'"),
        card("RADESYS =           'ICRS'"),
        card("CRVAL1  =                180.0"),
        card("CRVAL2  =                 20.0"),
        card("CRPIX1  =                 50.0"),
        card("CRPIX2  =                 50.0"),
        card("CD1_1   =               -0.01"),
        card("CD1_2   =                 0.0"),
        card("CD2_1   =                 0.0"),
        card("CD2_2   =                0.01"),
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
