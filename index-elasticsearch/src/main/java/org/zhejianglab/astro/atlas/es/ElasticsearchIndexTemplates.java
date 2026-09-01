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

package org.zhejianglab.astro.atlas.es;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.IndexContract;

/** Explicit strict mappings for the three current-state Warehouse indices. */
public final class ElasticsearchIndexTemplates {
  public static final String LAYER_TEMPLATE_NAME = "ast_layer_index_v1_template";
  public static final String FILE_TEMPLATE_NAME = "ast_file_index_v1_template";
  public static final String COVERAGE_TEMPLATE_NAME = "ast_coverage_index_v1_template";

  private ElasticsearchIndexTemplates() {}

  public static Map<String, Object> layerTemplate() {
    return load("layer-v1.json");
  }

  public static Map<String, Object> fileTemplate() {
    return load("file-v1.json");
  }

  public static Map<String, Object> coverageTemplate() {
    return load("coverage-v1.json");
  }

  public static Map<String, Object> layerMappings() {
    return mappings(layerTemplate());
  }

  public static Map<String, Object> fileMappings() {
    return mappings(fileTemplate());
  }

  public static Map<String, Object> coverageMappings() {
    return mappings(coverageTemplate());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mappings(Map<String, Object> template) {
    return (Map<String, Object>) ((Map<?, ?>) template.get("template")).get("mappings");
  }

  private static Map<String, Object> load(String name) {
    try (var stream = ElasticsearchIndexTemplates.class.getResourceAsStream("/index/" + name)) {
      if (stream == null) throw new IllegalStateException("missing index contract resource: " + name);
      return new ObjectMapper().readValue(stream, new TypeReference<>() {});
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("invalid index contract resource: " + name, exception);
    }
  }
}
