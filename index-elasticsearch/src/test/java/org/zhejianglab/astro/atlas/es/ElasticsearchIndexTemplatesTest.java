package org.zhejianglab.astro.atlas.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ElasticsearchIndexTemplatesTest {
  @Test
  void declaresStrictMappingsForFixedIndices() {
    Map<String, Object> fileMappings = ElasticsearchIndexTemplates.fileMappings();
    Map<String, Object> coverageMappings = ElasticsearchIndexTemplates.coverageMappings();
    assertEquals("strict", fileMappings.get("dynamic"));
    assertEquals("keyword", ((Map<?, ?>) ((Map<?, ?>) fileMappings.get("properties")).get("source_uri")).get("type"));
    assertEquals("integer", ((Map<?, ?>) ((Map<?, ?>) fileMappings.get("properties")).get("coverage_cells")).get("type"));
    assertEquals("long", ((Map<?, ?>) ((Map<?, ?>) coverageMappings.get("properties")).get("healpix_cell")).get("type"));
    assertTrue(ElasticsearchIndexTemplates.fileTemplate().containsKey("index_patterns"));
    assertTrue(ElasticsearchIndexTemplates.coverageTemplate().containsKey("template"));
  }
}
