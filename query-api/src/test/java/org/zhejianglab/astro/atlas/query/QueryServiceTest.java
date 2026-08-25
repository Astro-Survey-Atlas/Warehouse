package org.zhejianglab.astro.atlas.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zhejianglab.astro.atlas.core.CoordinateFrame;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;
import org.zhejianglab.astro.atlas.core.HealpixNesting;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.PointQuery;
import org.zhejianglab.astro.atlas.core.SourceIdentity;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;
import org.zhejianglab.astro.atlas.core.SpatialStatus;

class QueryServiceTest {
  @Test
  void joinsCoverageHitsAndDeduplicatesFiles() {
    String uri = "s3://survey/image.fits";
    String fileId = SourceIdentity.fileId(uri);
    long firstCell = Healpix.ang2pixNest(8, 180.25, -2.5);
    long secondCell = Healpix.ang2pixNest(8, 180.35, -2.5);
    FileAsset file = new FileAsset(fileId, uri, "image.fits", "s3://survey", FileType.FITS, 10L, null,
        Modality.of("image"), SpatialStatus.KNOWN, List.of(firstCell, secondCell), java.time.Instant.now());
    SpatialCoverage first = coverage(fileId, uri, firstCell);
    SpatialCoverage second = coverage(fileId, uri, secondCell);

    QueryService service = new QueryService(new IndexReader() {
      @Override
      public Page<SpatialCoverage> searchCoverage(Collection<Long> cells, int limit, String cursor) {
        return new Page<>(List.of(first, second), null);
      }

      @Override
      public Collection<FileAsset> findFiles(Collection<String> ids) {
        return List.of(file);
      }
    });

    FileSearchResponse response = service.search(new PointQuery(180.25, -2.5, 10, null));
    assertEquals(1, response.items().size());
    assertEquals(fileId, response.items().get(0).fileId());
    assertEquals(2, response.items().get(0).matchingCoverage().size());
  }

  private static SpatialCoverage coverage(String fileId, String uri, long cell) {
    return new SpatialCoverage(fileId, uri, 8, cell, CoordinateFrame.ICRS, HealpixNesting.NESTED,
        CoverageMethod.WCS, CoverageRole.FOOTPRINT, Modality.of("image"), null);
  }
}
