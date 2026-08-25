package org.zhejianglab.astro.atlas.operator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScannerSummaryParser {
  private static final Pattern SUMMARY = Pattern.compile(
      "phase=(\\S+) scanRunId=(\\S+) layerId=(\\S+) snapshot=(\\S+) discovered=(\\d+) processed=(\\d+) coverage=(\\d+) "
          + "catalogRows=(\\d+) catalogValid=(\\d+) catalogInvalid=(\\d+) errors=(\\d+) orders=(\\[[^]]*\\]|\\S+) evidence=(\\S+)");
  private static final Pattern LEGACY_SUMMARY = Pattern.compile(
      "phase=(\\S+) discovered=(\\d+) processed=(\\d+) coverage=(\\d+) "
          + "catalogRows=(\\d+) catalogValid=(\\d+) catalogInvalid=(\\d+) errors=(\\d+)");

  private ScannerSummaryParser() {}

  public static Map<String, Object> parse(String log) {
    if (log == null || log.isBlank()) return Map.of();
    String[] lines = log.split("\\R");
    for (int index = lines.length - 1; index >= 0; index--) {
      Matcher matcher = SUMMARY.matcher(lines[index].trim());
      if (matcher.find()) return parseCurrent(matcher);
      Matcher legacy = LEGACY_SUMMARY.matcher(lines[index].trim());
      if (!legacy.find()) continue;
      return parseLegacy(legacy);
    }
    return Map.of();
  }

  private static Map<String, Object> parseCurrent(Matcher matcher) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("phase", matcher.group(1));
      result.put("scanRunId", matcher.group(2));
      result.put("layerId", matcher.group(3));
      result.put("sourceSnapshotSha256", matcher.group(4));
      result.put("discoveredFileCount", integer(matcher, 5));
      result.put("processedItemCount", integer(matcher, 6));
      result.put("coverageRecordCount", integer(matcher, 7));
      result.put("catalogRowCount", integer(matcher, 8));
      result.put("validCatalogRowCount", integer(matcher, 9));
      result.put("invalidCatalogRowCount", integer(matcher, 10));
      result.put("errorCount", integer(matcher, 11));
      result.put("availableOrders", orders(matcher.group(12)));
      result.put("evidencePath", matcher.group(13));
      return result;
  }

  private static Map<String, Object> parseLegacy(Matcher matcher) {
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

  private static int integer(Matcher matcher, int group) {
    return Integer.parseInt(matcher.group(group));
  }

  private static List<Integer> orders(String value) {
    String text = value.trim();
    if (text.startsWith("[") && text.endsWith("]")) text = text.substring(1, text.length() - 1).trim();
    if (text.isBlank()) return List.of();
    List<Integer> result = new ArrayList<>();
    for (String item : text.split(",")) result.add(Integer.parseInt(item.trim()));
    return List.copyOf(result);
  }
}
