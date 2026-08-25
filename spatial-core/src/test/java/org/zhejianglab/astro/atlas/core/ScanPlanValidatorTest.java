package org.zhejianglab.astro.atlas.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScanPlanValidatorTest {
  @Test
  void acceptsLocalPlanWithCredentialReferences() {
    ScanPlan plan = new ScanPlan(1,
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local("/tmp/fixture")),
        Filters.empty(), List.of("default", "fits", "catalog", "coverage"), Modality.of("image"),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", new CredentialRef(null, null, "ES_USER", "ES_PASSWORD", null, null, null, null))));
    assertDoesNotThrow(() -> ScanPlanValidator.validate(plan));
  }

  @Test
  void rejectsRemotePlanWithoutEndpointOrLocation() {
    ScanPlan plan = new ScanPlan(1,
        new SourceSpec(new SourceConnector(SourceType.OSS, null, CredentialRef.none()), null),
        Filters.empty(), List.of("default"), null,
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())));
    assertThrows(PlanValidationException.class, () -> ScanPlanValidator.validate(plan));
  }

  @Test
  void rejectsUnknownHandlersBeforeEnumeration() {
    ScanPlan plan = new ScanPlan(1,
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local("/tmp")),
        Filters.empty(), List.of("default", "object"), null,
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())));
    assertThrows(PlanValidationException.class, () -> ScanPlanValidator.validate(plan));
  }

  @Test
  void rejectsPartialConfiguredCatalogCoordinates() {
    ScanPlan plan = new ScanPlan(1,
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, CredentialRef.none()), SourceLocation.local("/tmp")),
        Filters.empty(), List.of("default", "catalog"), null,
        new CatalogSpec("ra_col", null, null, null),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://localhost:9200", CredentialRef.none())));
    assertThrows(PlanValidationException.class, () -> ScanPlanValidator.validate(plan));
  }
}
