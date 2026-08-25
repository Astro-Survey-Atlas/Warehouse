package org.zhejianglab.astro.atlas.operator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScannerSummaryParser {
  private static final Pattern SUMMARY = Pattern.compile(
      "phase=(\\S+) discovered=(\\d+) processed=(\\d+) coverage=(\\d+) "
          + "catalogRows=(\\d+) catalogValid=(\\d+) catalogInvalid=(\\d+) errors=(\\d+)");

  private ScannerSummaryParser() {}

  public static Map<String, Object> parse(String log) {
    if (log == null || log.isBlank()) return Map.of();
    String[] lines = log.split("\\R");
    for (int index = lines.length - 1; index >= 0; index--) {
      Matcher matcher = SUMMARY.matcher(lines[index].trim());
      if (!matcher.find()) continue;
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("phase", matcher.group(1));
      result.put("discoveredFileCount", integer(matcher, 2));
      result.put("processedItemCount", integer(matcher, 3));
      result.put("coverageRecordCount", integer(matcher, 4));
      result.put("catalogRowCount", integer(matcher, 5));
      result.put("validCatalogRowCount", integer(matcher, 6));
      result.put("invalidCatalogRowCount", integer(matcher, 7));
      result.put("errorCount", integer(matcher, 8));
      return result;
    }
    return Map.of();
  }

  private static int integer(Matcher matcher, int group) {
    return Integer.parseInt(matcher.group(group));
  }
}
