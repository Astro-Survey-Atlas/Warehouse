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
    assertTrue(ElasticsearchIndexTemplates.fileTemplate().containsKey("index_patterns"));
    assertTrue(ElasticsearchIndexTemplates.coverageTemplate().containsKey("template"));
  }
}
