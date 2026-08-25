package org.zhejianglab.astro.atlas.scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;

/** Minimal header-driven CSV/TSV extraction for the first local vertical slice. */
public final class CatalogHandler implements Handler {
  @Override
  public void handle(ScanContext context) throws IOException {
    if (context.item().fileType() != FileType.CSV && context.item().fileType() != FileType.TSV) return;
    char delimiter = context.item().fileType() == FileType.TSV ? '\t' : ',';
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.content().open(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null) return;
      String[] headers = split(headerLine, delimiter);
      Map<String, Integer> columns = columns(headers);
      Integer raColumn = first(columns, "ra", "ra_deg", "raj2000");
      Integer decColumn = first(columns, "dec", "dec_deg", "dej2000");
      Integer pixelColumn = first(columns, "healpix_cell", "healpix", "pixel");
      Integer orderColumn = first(columns, "healpix_order", "order");
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;
        String[] values = split(line, delimiter);
        if (raColumn != null && decColumn != null) {
          Double ra = parse(values, raColumn);
          Double dec = parse(values, decColumn);
          if (ra == null || dec == null) {
            context.addError("malformed catalog coordinate");
            continue;
          }
          try {
            context.addCoverage(Healpix.ang2pixNest(8, ra, dec), CoverageMethod.CATALOG_COORDINATES);
          } catch (IllegalArgumentException exception) {
            context.addError("invalid catalog coordinate: " + exception.getMessage());
          }
        } else if (pixelColumn != null) {
          Long pixel = parseLong(values, pixelColumn);
          int order = orderColumn == null ? 8 : parseLong(values, orderColumn) == null ? -1 : parseLong(values, orderColumn).intValue();
          if (pixel == null || order < 0) {
            context.addError("malformed catalog HEALPix value");
            continue;
          }
          try {
            for (long cell : Healpix.normalizeQueryCells(order, pixel)) context.addCoverage(cell, CoverageMethod.CATALOG_HEALPIX);
          } catch (IllegalArgumentException exception) {
            context.addError("invalid catalog HEALPix value: " + exception.getMessage());
          }
        }
      }
    }
  }

  private static Map<String, Integer> columns(String[] headers) {
    Map<String, Integer> result = new HashMap<>();
    for (int index = 0; index < headers.length; index++) {
      result.put(headers[index].replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT), index);
    }
    return result;
  }

  private static Integer first(Map<String, Integer> columns, String... names) {
    for (String name : names) if (columns.containsKey(name)) return columns.get(name);
    return null;
  }

  private static Double parse(String[] values, int index) {
    if (index >= values.length || values[index].isBlank()) return null;
    try {
      return Double.parseDouble(values[index].trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static Long parseLong(String[] values, int index) {
    if (index >= values.length || values[index].isBlank()) return null;
    try {
      return Long.parseLong(values[index].trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static String[] split(String line, char delimiter) {
    return line.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1);
  }
}
