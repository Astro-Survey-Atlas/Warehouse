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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ElasticsearchIndexTemplatesTest {
  @Test
  void declaresStrictMappingsForFixedIndices() {
    Map<String, Object> layerMappings = ElasticsearchIndexTemplates.layerMappings();
    Map<String, Object> fileMappings = ElasticsearchIndexTemplates.fileMappings();
    Map<String, Object> coverageMappings = ElasticsearchIndexTemplates.coverageMappings();
    assertEquals("strict", layerMappings.get("dynamic"));
    assertEquals("strict", fileMappings.get("dynamic"));
    assertEquals("keyword", ((Map<?, ?>) ((Map<?, ?>) fileMappings.get("properties")).get("source_uri")).get("type"));
    assertEquals("integer", ((Map<?, ?>) ((Map<?, ?>) layerMappings.get("properties")).get("available_orders")).get("type"));
    assertEquals("long", ((Map<?, ?>) ((Map<?, ?>) coverageMappings.get("properties")).get("healpix_cell")).get("type"));
    assertEquals("keyword", ((Map<?, ?>) ((Map<?, ?>) coverageMappings.get("properties")).get("precision")).get("type"));
    assertEquals(2048, ((Map<?, ?>) ((Map<?, ?>) fileMappings.get("properties")).get("source_uri")).get("ignore_above"));
    assertTrue(ElasticsearchIndexTemplates.fileTemplate().containsKey("index_patterns"));
    assertTrue(ElasticsearchIndexTemplates.coverageTemplate().containsKey("template"));
  }
}
