package org.zhejianglab.astro.atlas.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScanPlanValidatorTest {
  @Test
  void acceptsLocalPlanWithCredentialReferences() {
    ScanPlan plan = new ScanPlan(2, "local-image-20260825",
        new LayerSpec("local-image", "test-survey", "local", "image", Modality.IMAGE, CoverageRole.FOOTPRINT, null),
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local("/tmp/fixture")),
        Filters.empty(), new ExtractionSpec(ExtractionMode.FITS_HEADER_POSITION, 8, CatalogSpec.empty()),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", new CredentialRef(null, null, "ES_USER", "ES_PASSWORD", null, null, null, null))),
        new EvidenceSpec("/tmp/atlas-evidence/local-image"));
    assertDoesNotThrow(() -> ScanPlanValidator.validate(plan));
  }

  @Test
  void rejectsRemotePlanWithoutEndpointOrLocation() {
    ScanPlan plan = new ScanPlan(2, "remote-invalid-20260825",
        new LayerSpec("remote-invalid", "test-survey", "local", "image", Modality.IMAGE, CoverageRole.FOOTPRINT, null),
        new SourceSpec(new SourceConnector(SourceType.OSS, null, CredentialRef.none()), null),
        Filters.empty(), new ExtractionSpec(ExtractionMode.FITS_WCS, 8, CatalogSpec.empty()),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())),
        new EvidenceSpec("/tmp/atlas-evidence/remote-invalid"));
    assertThrows(PlanValidationException.class, () -> ScanPlanValidator.validate(plan));
  }

  @Test
  void rejectsVersionOneHandlerPlanBeforeEnumeration() {
    ScanPlan plan = new ScanPlan(1, "old-plan",
        new LayerSpec("old-layer", "test-survey", "local", "image", Modality.IMAGE, CoverageRole.FOOTPRINT, null),
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local("/tmp")),
        Filters.empty(), new ExtractionSpec(ExtractionMode.FITS_WCS, 8, CatalogSpec.empty()),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())),
        new EvidenceSpec("/tmp/atlas-evidence/old"));
    assertThrows(PlanValidationException.class, () -> ScanPlanValidator.validate(plan));
  }

  @Test
  void rejectsPartialConfiguredCatalogCoordinates() {
    ScanPlan plan = new ScanPlan(2, "bad-catalog-20260825",
        new LayerSpec("bad-catalog", "test-survey", "local", "catalog", Modality.CATALOG, CoverageRole.OCCUPANCY, null),
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local("/tmp")),
        Filters.empty(), new ExtractionSpec(ExtractionMode.CATALOG_RADEC, 8,
            new CatalogSpec("ra_col", null, null, null, null)),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())),
        new EvidenceSpec("/tmp/atlas-evidence/bad-catalog"));
    assertThrows(PlanValidationException.class, () -> ScanPlanValidator.validate(plan));
  }
}
