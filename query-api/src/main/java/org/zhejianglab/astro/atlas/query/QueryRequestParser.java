package org.zhejianglab.astro.atlas.query;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw ApiException.invalid("query parameter is not valid URL encoding", null);
    }
  }
}
