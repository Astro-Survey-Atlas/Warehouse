package org.zhejianglab.astro.atlas.core;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ScanPlanValidator {
  private static final Pattern RUN_ID = Pattern.compile("[a-z0-9][a-z0-9-]*");

  private ScanPlanValidator() {}

  public static void validate(ScanPlan plan) {
    validate(plan, false);
  }

  public static void validate(ScanPlan plan, boolean memoryMode) {
    List<String> errors = new ArrayList<>();
    if (plan == null) throw new PlanValidationException(List.of("plan is required"));
    if (plan.version() == null || plan.version() != 2) errors.add("version must be 2; Handler-based version 1 plans are unsupported");
    if (plan.scanRunId() == null || !RUN_ID.matcher(plan.scanRunId()).matches()) errors.add("scanRunId must be a lowercase ID");
    if (plan.layer() == null) errors.add("layer is required");
    else validateLayer(plan.layer(), errors);
    if (plan.source() == null || plan.source().connector() == null) errors.add("source.connector is required");
    else validateSource(plan.source(), errors);
    validateExtraction(plan.extraction(), plan.layer(), errors);
    if (plan.sink() == null || plan.sink().connector() == null) errors.add("sink.connector is required");
    else validateSink(plan.sink(), errors);
    if (!memoryMode && (plan.evidence() == null || plan.evidence().outputPath() == null)) {
      errors.add("evidence.outputPath is required for persisted scans");
    }
    if (plan.evidence() != null && plan.evidence().outputPath() != null) {
      try {
        Path.of(plan.evidence().outputPath());
      } catch (RuntimeException exception) {
        errors.add("evidence.outputPath is invalid");
      }
    }
    if (!errors.isEmpty()) throw new PlanValidationException(errors);
  }

  private static void validateExtraction(ExtractionSpec extraction, LayerSpec layer, List<String> errors) {
    if (extraction == null || extraction.mode() == null) {
      errors.add("extraction.mode is required");
      return;
    }
    CatalogSpec catalog = extraction.catalog();
    boolean catalogEmpty = catalog == null || (catalog.raColumn() == null && catalog.decColumn() == null
        && catalog.healpixColumn() == null && catalog.healpixOrderColumn() == null && catalog.healpixOrder() == null);
    switch (extraction.mode()) {
      case FITS_WCS, FITS_HEADER_POSITION -> {
        validateComputedOrder(extraction.outputOrder(), errors);
        if (!catalogEmpty) errors.add("FITS extraction cannot contain catalog settings");
      }
      case CATALOG_RADEC -> {
        validateComputedOrder(extraction.outputOrder(), errors);
        if (catalog == null || catalog.raColumn() == null || catalog.decColumn() == null) {
          errors.add("catalog-radec requires catalog.raColumn and catalog.decColumn");
        }
        if (catalog != null && (catalog.healpixColumn() != null || catalog.healpixOrderColumn() != null || catalog.healpixOrder() != null)) {
          errors.add("catalog-radec cannot contain HEALPix settings");
        }
      }
      case CATALOG_HEALPIX -> {
        if (extraction.outputOrder() != null) errors.add("catalog-healpix preserves source order and cannot set outputOrder");
        if (catalog == null || catalog.healpixColumn() == null) errors.add("catalog-healpix requires catalog.healpixColumn");
        boolean fixed = catalog != null && catalog.healpixOrder() != null;
        boolean column = catalog != null && catalog.healpixOrderColumn() != null;
        if (fixed == column) errors.add("catalog-healpix requires exactly one of catalog.healpixOrder or catalog.healpixOrderColumn");
        if (fixed) {
          try { Healpix.validateOrder(catalog.healpixOrder()); }
          catch (IllegalArgumentException exception) { errors.add(exception.getMessage()); }
        }
        if (catalog != null && (catalog.raColumn() != null || catalog.decColumn() != null)) errors.add("catalog-healpix cannot contain RA/Dec settings");
      }
    }
    if (layer != null && extraction.mode() == ExtractionMode.CATALOG_RADEC && layer.coverageRole() != CoverageRole.OCCUPANCY) {
      errors.add("catalog-radec requires layer.coverageRole=occupancy");
    }
  }

  private static void validateLayer(LayerSpec layer, List<String> errors) {
    try {
      new LayerSpec(layer.layerId(), layer.surveyId(), layer.releaseId(), layer.productId(),
          layer.modality(), layer.coverageRole(), layer.entrypoint());
    } catch (IllegalArgumentException exception) {
      errors.add(exception.getMessage());
    }
  }

  private static void validateComputedOrder(Integer order, List<String> errors) {
    if (order == null) {
      errors.add("extraction.outputOrder is required");
      return;
    }
    try {
      Healpix.validateOrder(order);
      if (order > Healpix.MAX_COMPUTED_ORDER) errors.add("computed outputOrder must be 0.." + Healpix.MAX_COMPUTED_ORDER);
    } catch (IllegalArgumentException exception) {
      errors.add(exception.getMessage());
    }
  }

  private static void validateSource(SourceSpec source, List<String> errors) {
    SourceConnector connector = source.connector();
    if (connector.type() == null) errors.add("source.connector.type is required");
    SourceLocation location = source.location();
    if (location == null) errors.add("source.location is required");
    else if (connector.type() == SourceType.LOCAL) {
      if (location.rootPath() == null || location.rootPath().isBlank()) errors.add("local source requires location.rootPath");
      if (location.bucket() != null || location.prefix() != null) errors.add("local source cannot use bucket or prefix");
    } else {
      if (location.bucket() == null || location.bucket().isBlank()) errors.add("object source requires location.bucket");
      if (location.rootPath() != null) errors.add("object source cannot use location.rootPath");
      validateHttpEndpoint(connector.endpoint(), "source.connector.endpoint", errors);
    }
    validateCredentials(connector.credentialRef(), "source.connector.credentialRef", errors);
  }

  private static void validateSink(SinkSpec sink, List<String> errors) {
    SinkConnector connector = sink.connector();
    if (connector.type() != SinkType.ELASTICSEARCH) errors.add("sink connector must be elasticsearch");
    validateHttpEndpoint(connector.endpoint(), "sink.connector.endpoint", errors);
    validateCredentials(connector.credentialRef(), "sink.connector.credentialRef", errors);
  }

  private static void validateCredentials(CredentialRef ref, String field, List<String> errors) {
    if (ref == null) return;
    try { ref.validate(); }
    catch (IllegalArgumentException exception) { errors.add(field + ": " + exception.getMessage()); }
  }

  private static void validateHttpEndpoint(String value, String field, List<String> errors) {
    if (value == null || value.isBlank()) {
      errors.add(field + " is required");
      return;
    }
    try {
      URI uri = URI.create(value);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
        errors.add(field + " must be an http or https URI");
      }
    } catch (IllegalArgumentException exception) {
      errors.add(field + " must be a valid URI");
    }
  }
}
