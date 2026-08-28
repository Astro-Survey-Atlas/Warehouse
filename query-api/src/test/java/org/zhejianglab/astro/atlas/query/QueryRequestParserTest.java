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

package org.zhejianglab.astro.atlas.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.zhejianglab.astro.atlas.core.ConeQuery;
import org.zhejianglab.astro.atlas.core.PointQuery;

class QueryRequestParserTest {
  @Test
  void parsesPointAndDefaultsLimit() {
    var query = QueryRequestParser.parse("/v1/files/point", "ra=180.25&dec=-2.5");
    assertEquals(PointQuery.class, query.getClass());
    assertEquals(100, query.limit());
  }

  @Test
  void rejectsMalformedAndInvalidQueries() {
    assertThrows(ApiException.class, () -> QueryRequestParser.parse("/v1/files/point", "ra=180.25"));
    assertThrows(ApiException.class, () -> QueryRequestParser.parse("/v1/files/cone", "ra=180&dec=0&radiusDeg=0"));
    assertThrows(ApiException.class, () -> QueryRequestParser.parse("/v1/files/point", "ra=180&dec=0&limit=1001"));
  }

  @Test
  void parsesConeCursor() {
    var query = QueryRequestParser.parse("/v1/files/cone", "ra=180&dec=0&radiusDeg=1&cursor=opaque");
    assertEquals(ConeQuery.class, query.getClass());
    assertEquals("opaque", query.cursor());
  }

  @Test
  void parsesExplicitLayerOrderAndPixels() {
    long pixel = 12L;
    var lookup = QueryRequestParser.parseCoverageLookup("/v2/files/healpix",
        "layers=desi,csst&order=4&pixels=" + pixel + ",13&limit=20");
    assertEquals(java.util.Set.of("desi", "csst"), lookup.layerIds());
    assertEquals(4, lookup.order());
    assertEquals(java.util.Set.of(pixel, 13L), lookup.pixels());
    assertEquals(20, lookup.limit());
  }

  @Test
  void diagnosticPointRequiresLayers() {
    assertThrows(ApiException.class, () -> QueryRequestParser.parseDiagnostic("/v1/files/point", "ra=180&dec=0"));
  }
}
