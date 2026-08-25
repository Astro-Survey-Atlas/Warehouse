package org.zhejianglab.astro.atlas.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScanPlanValidator {
  private static final Set<String> HANDLERS = Set.of("default", "fits", "catalog", "coverage");

  private ScanPlanValidator() {}

  public static void validate(ScanPlan plan) {
    List<String> errors = new ArrayList<>();
    if (plan == null) {
      throw new PlanValidationException(List.of("plan is required"));
    }
    if (plan.version() == null || plan.version() != 1) errors.add("version must be 1");
    if (plan.source() == null || plan.source().connector() == null) {
      errors.add("source.connector is required");
    } else {
      validateSource(plan.source(), errors);
    }
    if (plan.handlers() == null || plan.handlers().isEmpty()) {
      errors.add("handlers must not be empty");
    } else {
      Set<String> seen = new HashSet<>();
      for (String handler : plan.handlers()) {
        String normalized = handler == null ? "" : handler.toLowerCase(Locale.ROOT);
        if (!HANDLERS.contains(normalized)) errors.add("unknown handler: " + handler);
        if (!seen.add(normalized)) errors.add("duplicate handler: " + handler);
      }
    }
    validateCatalog(plan.catalog(), errors);
    if (plan.sink() == null || plan.sink().connector() == null) {
      errors.add("sink.connector is required");
    } else {
      SinkConnector connector = plan.sink().connector();
      if (connector.type() != SinkType.ELASTICSEARCH) errors.add("sink connector must be elasticsearch");
      validateHttpEndpoint(connector.endpoint(), "sink.connector.endpoint", errors);
      validateCredentials(connector.credentialRef(), "sink.connector.credentialRef", errors);
    }
    if (!errors.isEmpty()) throw new PlanValidationException(errors);
  }

  private static void validateSource(SourceSpec source, List<String> errors) {
    SourceConnector connector = source.connector();
    if (connector.type() == null) errors.add("source.connector.type is required");
    SourceLocation location = source.location();
    if (location == null) {
      errors.add("source.location is required");
    } else if (connector.type() == SourceType.LOCAL) {
      if (location.rootPath() == null || location.rootPath().isBlank()) errors.add("local source requires location.rootPath");
      if (location.bucket() != null || location.prefix() != null) errors.add("local source cannot use bucket or prefix");
    } else {
      if (location.bucket() == null || location.bucket().isBlank()) errors.add("object source requires location.bucket");
      if (location.rootPath() != null) errors.add("object source cannot use location.rootPath");
      validateHttpEndpoint(connector.endpoint(), "source.connector.endpoint", errors);
    }
    validateCredentials(connector.credentialRef(), "source.connector.credentialRef", errors);
  }

  private static void validateCredentials(CredentialRef ref, String field, List<String> errors) {
    if (ref == null) return;
    try {
      ref.validate();
    } catch (IllegalArgumentException exception) {
      errors.add(field + ": " + exception.getMessage());
    }
  }

  private static void validateCatalog(CatalogSpec catalog, List<String> errors) {
    if (catalog == null) return;
    if ((catalog.raColumn() == null) != (catalog.decColumn() == null)) {
      errors.add("catalog.raColumn and catalog.decColumn must be supplied together");
    }
    if (catalog.raColumn() != null && catalog.healpixColumn() != null) {
      errors.add("catalog coordinate columns and catalog.healpixColumn cannot be selected together");
    }
    if (catalog.healpixOrderColumn() != null && catalog.healpixColumn() == null) {
      errors.add("catalog.healpixOrderColumn requires catalog.healpixColumn");
    }
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
