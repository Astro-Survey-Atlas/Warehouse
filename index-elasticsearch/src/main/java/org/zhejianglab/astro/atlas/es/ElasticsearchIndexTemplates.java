package org.zhejianglab.astro.atlas.es;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.IndexContract;

/** Explicit Elasticsearch mappings for the two fixed product indices. */
public final class ElasticsearchIndexTemplates {
  public static final String FILE_TEMPLATE_NAME = "ast_file_index_v1_template";
  public static final String COVERAGE_TEMPLATE_NAME = "ast_coverage_index_v1_template";

  private ElasticsearchIndexTemplates() {}

  public static Map<String, Object> fileTemplate() {
    return template(FILE_TEMPLATE_NAME, IndexContract.FILE_INDEX, fileMappings());
  }

  public static Map<String, Object> coverageTemplate() {
    return template(COVERAGE_TEMPLATE_NAME, IndexContract.COVERAGE_INDEX, coverageMappings());
  }

  public static Map<String, Object> fileMappings() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("file_id", keyword());
    properties.put("source_uri", keyword());
    properties.put("file_name", keyword());
    properties.put("parent_uri", keyword());
    properties.put("file_type", keyword());
    properties.put("size_bytes", Map.of("type", "long"));
    properties.put("last_modified", Map.of("type", "date"));
    properties.put("modality", keyword());
    properties.put("spatial_status", keyword());
    properties.put("coverage_cells", Map.of("type", "integer"));
    properties.put("indexed_at", Map.of("type", "date"));
    return mappings(properties);
  }

  public static Map<String, Object> coverageMappings() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("source_file_id", keyword());
    properties.put("source_uri", keyword());
    properties.put("healpix_order", Map.of("type", "integer"));
    properties.put("healpix_cell", Map.of("type", "long"));
    properties.put("coordinate_frame", keyword());
    properties.put("nesting", keyword());
    properties.put("coverage_method", keyword());
    properties.put("coverage_role", keyword());
    properties.put("modality", keyword());
    properties.put("quality", keyword());
    return mappings(properties);
  }

  private static Map<String, Object> template(String name, String index, Map<String, Object> mappings) {
    Map<String, Object> template = new LinkedHashMap<>();
    template.put("index_patterns", List.of(index));
    template.put("priority", 100);
    template.put("template", Map.of("mappings", mappings));
    return template;
  }

  private static Map<String, Object> mappings(Map<String, Object> properties) {
    Map<String, Object> mappings = new LinkedHashMap<>();
    mappings.put("dynamic", "strict");
    mappings.put("properties", properties);
    return mappings;
  }

  private static Map<String, Object> keyword() {
    return Map.of("type", "keyword", "ignore_above", 2048);
  }
}
