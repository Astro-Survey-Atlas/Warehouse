/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zhejianglab.astro.atlas.scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CatalogSpec;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoveragePrecision;
import org.zhejianglab.astro.atlas.core.ExtractionMode;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceContent;

/** Reads quoted CSV/TSV rows and emits one de-duplicated coverage set per file. */
public final class CatalogHandler implements CoverageExtractor {
  private static final String[] RA_ALIASES = {"ra", "ra_deg", "raj2000"};
  private static final String[] DEC_ALIASES = {"dec", "dec_deg", "dej2000"};
  private static final String[] PIXEL_ALIASES = {"healpix_cell", "healpix", "pixel"};
  private static final String[] ORDER_ALIASES = {"healpix_order", "order"};

  @Override
  public ExtractionResult extract(InputItem item, SourceContent content, ScanPlan plan) {
    ScanContext context = new ScanContext(item, content, plan);
    try {
      extractInto(context);
    } catch (IOException exception) {
      context.addError("catalog read failed: " + exception.getMessage());
    }
    return context.result();
  }

  private void extractInto(ScanContext context) throws IOException {
    if (context.item().fileType() != FileType.CSV && context.item().fileType() != FileType.TSV
        && context.item().fileType() != FileType.CATALOG) return;
    boolean whitespaceCatalog = context.item().fileType() == FileType.CATALOG;
    char delimiter = context.item().fileType() == FileType.TSV ? '\t' : ',';
    CatalogSpec spec = context.plan().extraction().catalog();
    ExtractionMode mode = context.plan().extraction().mode();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.content().open(), StandardCharsets.UTF_8))) {
      String headerLine = readRecord(reader, delimiter);
      if (headerLine == null) {
        context.addError("catalog header is missing");
        return;
      }
      List<String> headers = whitespaceCatalog ? parseWhitespace(headerLine) : parse(headerLine, delimiter);
      if (whitespaceCatalog && !headers.isEmpty() && "#".equals(headers.get(0))) {
        headers = headers.subList(1, headers.size());
      }
      Map<String, Integer> columns = columns(headers);
      boolean coordinateMode = mode == ExtractionMode.CATALOG_RADEC;
      boolean healpixMode = mode == ExtractionMode.CATALOG_HEALPIX;
      Integer raColumn = healpixMode ? null : first(columns, spec.raColumn(), RA_ALIASES);
      Integer decColumn = healpixMode ? null : first(columns, spec.decColumn(), DEC_ALIASES);
      Integer pixelColumn = coordinateMode ? null : first(columns, spec.healpixColumn(), PIXEL_ALIASES);
      Integer orderColumn = coordinateMode ? null : first(columns, spec.healpixOrderColumn(), ORDER_ALIASES);
      if (coordinateMode && (raColumn == null || decColumn == null)) {
        context.addError("configured catalog RA/Dec columns were not found");
        return;
      }
      if (healpixMode && pixelColumn == null) {
        context.addError("configured catalog HEALPix column was not found");
        return;
      }
      if (healpixMode && orderColumn == null && spec.healpixOrder() == null) {
        context.addError("catalog HEALPix order column or fixed order was not found");
        return;
      }

      String line;
      while ((line = readRecord(reader, delimiter)) != null) {
        if (line.isBlank()) continue;
        if (whitespaceCatalog && line.stripLeading().startsWith("#")) continue;
        context.addCatalogRow();
        try {
          List<String> values = whitespaceCatalog ? parseWhitespace(line) : parse(line, delimiter);
          if (raColumn != null && decColumn != null) {
            Double ra = parse(values, raColumn);
            Double dec = parse(values, decColumn);
            if (ra == null || dec == null) throw new IllegalArgumentException("malformed catalog coordinate");
            int order = context.plan().extraction().outputOrder();
            context.addCoverage(order, Healpix.ang2pixNest(order, ra, dec),
                CoverageMethod.CATALOG_RADEC, CoveragePrecision.EXACT, null);
          } else if (pixelColumn != null) {
            Long pixel = parseLong(values, pixelColumn);
            Long parsedOrder = orderColumn == null
                ? (spec.healpixOrder() == null ? null : spec.healpixOrder().longValue())
                : parseLong(values, orderColumn);
            if (pixel == null || parsedOrder == null
                || parsedOrder < Integer.MIN_VALUE || parsedOrder > Integer.MAX_VALUE) {
              throw new IllegalArgumentException("malformed catalog HEALPix value");
            }
            int sourceOrder = parsedOrder.intValue();
            Healpix.validateCell(sourceOrder, pixel);
            context.addCoverage(sourceOrder, pixel, CoverageMethod.CATALOG_HEALPIX,
                CoveragePrecision.EXACT, sourceOrder);
          } else {
            continue;
          }
          context.addValidCatalogRow();
        } catch (RuntimeException exception) {
          context.addInvalidCatalogRow();
          context.addError(exception.getMessage() == null ? "malformed catalog spatial value" : exception.getMessage());
          // A malformed row makes the file result unreliable. Stop this file so
          // the scan can fail immediately instead of hiding the bad input.
          break;
        }
      }
    }
  }

  private static Map<String, Integer> columns(List<String> headers) {
    Map<String, Integer> result = new HashMap<>();
    for (int index = 0; index < headers.size(); index++) {
      result.putIfAbsent(normalize(headers.get(index)), index);
    }
    return result;
  }

  private static Integer first(Map<String, Integer> columns, String configured, String[] aliases) {
    if (configured != null) return columns.get(normalize(configured));
    for (String alias : aliases) {
      Integer index = columns.get(alias);
      if (index != null) return index;
    }
    return null;
  }

  private static Double parse(List<String> values, int index) {
    if (index >= values.size() || values.get(index).isBlank()) return null;
    try {
      double value = Double.parseDouble(values.get(index).trim());
      return Double.isFinite(value) ? value : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static Long parseLong(List<String> values, int index) {
    if (index >= values.size() || values.get(index).isBlank()) return null;
    try {
      return Long.parseLong(values.get(index).trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static String readRecord(BufferedReader reader, char delimiter) throws IOException {
    String line = reader.readLine();
    if (line == null) return null;
    StringBuilder record = new StringBuilder(line);
    while (hasOpenQuote(record, delimiter)) {
      String continuation = reader.readLine();
      if (continuation == null) throw new IOException("unterminated quoted catalog field");
      record.append('\n').append(continuation);
    }
    return record.toString();
  }

  private static boolean hasOpenQuote(CharSequence value, char delimiter) {
    boolean quoted = false;
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) != '"') continue;
      if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '"') {
        index++;
      } else {
        quoted = !quoted;
      }
    }
    return quoted;
  }

  private static List<String> parse(String line, char delimiter) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    StringBuilder value = new StringBuilder();
    boolean quoted = false;
    boolean quoteClosed = false;
    for (int index = 0; index < line.length(); index++) {
      char current = line.charAt(index);
      if (current == '"') {
        if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
          value.append('"');
          index++;
        } else if (!quoteClosed) {
          quoted = !quoted;
          if (!quoted) quoteClosed = true;
        } else {
          throw new IllegalArgumentException("malformed quoted catalog field");
        }
      } else if (current == delimiter && !quoted) {
        values.add(value.toString().trim());
        value.setLength(0);
        quoteClosed = false;
      } else {
        if (quoteClosed && !Character.isWhitespace(current)) throw new IllegalArgumentException("malformed quoted catalog field");
        value.append(current);
      }
    }
    if (quoted) throw new IllegalArgumentException("unterminated quoted catalog field");
    values.add(value.toString().trim());
    return List.copyOf(values);
  }

  private static List<String> parseWhitespace(String line) {
    String value = line == null ? "" : line.trim();
    return value.isEmpty() ? List.of() : List.of(value.split("\\s+"));
  }

  private static String normalize(String value) {
    return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
  }
}
