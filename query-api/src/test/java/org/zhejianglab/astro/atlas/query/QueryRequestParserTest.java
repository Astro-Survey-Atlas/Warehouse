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
}
