package org.zhejianglab.astro.atlas.query;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.zhejianglab.astro.atlas.core.CoverageLookup;
import org.zhejianglab.astro.atlas.core.ConeQuery;
import org.zhejianglab.astro.atlas.core.HealpixQuery;
import org.zhejianglab.astro.atlas.core.PointQuery;
import org.zhejianglab.astro.atlas.core.QueryLimits;
import org.zhejianglab.astro.atlas.core.SpatialQuery;

public final class QueryRequestParser {
  private QueryRequestParser() {}

  public static SpatialQuery parse(String route, String rawQuery) {
    Map<String, String> parameters = parameters(rawQuery);
    int limit = integer(parameters, "limit", QueryLimits.DEFAULT);
    String cursor = parameters.get("cursor");
    try {
      return switch (route) {
        case "/v1/files/point" -> new PointQuery(requiredDouble(parameters, "ra"), requiredDouble(parameters, "dec"), limit, cursor);
        case "/v1/files/cone" -> new ConeQuery(requiredDouble(parameters, "ra"), requiredDouble(parameters, "dec"), requiredDouble(parameters, "radiusDeg"), limit, cursor);
        case "/v1/files/healpix" -> new HealpixQuery(integer(parameters, "order", Integer.MIN_VALUE), longValue(parameters, "pixel"), limit, cursor);
        default -> throw new ApiException(404, "NOT_FOUND", "route was not found", null);
      };
    } catch (ApiException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw ApiException.invalid(exception.getMessage(), null);
    }
  }

  public static DiagnosticRequest parseDiagnostic(String route, String rawQuery) {
    Map<String, String> parameters = parameters(rawQuery);
    SpatialQuery query = parse(route, rawQuery);
    return new DiagnosticRequest(query, list(parameters, "layers"));
  }

  public static CoverageLookup parseCoverageLookup(String route, String rawQuery) {
    if (!"/v2/files/healpix".equals(route)) throw new ApiException(404, "NOT_FOUND", "route was not found", null);
    Map<String, String> parameters = parameters(rawQuery);
    try {
      Set<String> layers = list(parameters, "layers");
      int order = integer(parameters, "order", Integer.MIN_VALUE);
      Set<Long> pixels = longList(parameters, "pixels");
      int limit = integer(parameters, "limit", QueryLimits.DEFAULT);
      return new CoverageLookup(layers, order, pixels, limit, parameters.get("cursor"));
    } catch (ApiException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw ApiException.invalid(exception.getMessage(), null);
    }
  }

  private static Map<String, String> parameters(String rawQuery) {
    Map<String, String> result = new HashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) return result;
    for (String pair : rawQuery.split("&", -1)) {
      int separator = pair.indexOf('=');
      if (separator <= 0) throw ApiException.invalid("query parameter must have a name and value", null);
      String name = decode(pair.substring(0, separator));
      String value = decode(pair.substring(separator + 1));
      if (name.isBlank() || result.put(name, value) != null) throw ApiException.invalid("query parameter is duplicated", name);
    }
    return result;
  }

  private static String required(Map<String, String> parameters, String name) {
    String value = parameters.get(name);
    if (value == null || value.isBlank()) throw ApiException.invalid(name + " is required", name);
    return value;
  }

  private static double requiredDouble(Map<String, String> parameters, String name) {
    String value = required(parameters, name);
    try {
      double parsed = Double.parseDouble(value);
      if (!Double.isFinite(parsed)) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      throw ApiException.invalid(name + " must be a finite number", name);
    }
  }

  private static int integer(Map<String, String> parameters, String name, int defaultValue) {
    String value = parameters.get(name);
    if (value == null && defaultValue != Integer.MIN_VALUE) return defaultValue;
    if (value == null || value.isBlank()) throw ApiException.invalid(name + " is required", name);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw ApiException.invalid(name + " must be an integer", name);
    }
  }

  private static long longValue(Map<String, String> parameters, String name) {
    String value = required(parameters, name);
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw ApiException.invalid(name + " must be an integer", name);
    }
  }

  private static Set<String> list(Map<String, String> parameters, String name) {
    String value = required(parameters, name);
    Set<String> result = new LinkedHashSet<>();
    for (String item : value.split(",", -1)) {
      if (item.isBlank()) throw ApiException.invalid(name + " must not contain blank values", name);
      result.add(item.trim());
    }
    if (result.isEmpty()) throw ApiException.invalid(name + " must not be empty", name);
    return Set.copyOf(result);
  }

  private static Set<Long> longList(Map<String, String> parameters, String name) {
    Set<String> values = list(parameters, name);
    Set<Long> result = new LinkedHashSet<>();
    for (String value : values) {
      try {
        result.add(Long.parseLong(value));
      } catch (NumberFormatException exception) {
        throw ApiException.invalid(name + " must contain integers", name);
      }
    }
    return Set.copyOf(result);
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw ApiException.invalid("query parameter is not valid URL encoding", null);
    }
  }

  public record DiagnosticRequest(SpatialQuery query, Set<String> layerIds) {
    public DiagnosticRequest {
      layerIds = Set.copyOf(layerIds);
    }
  }
}
