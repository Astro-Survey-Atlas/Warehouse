package org.zhejianglab.astro.atlas.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpatialCoverageTest {
  @Test
  void rejectsCoverageWhoseFileIdDoesNotMatchSourceUri() {
    assertThrows(IllegalArgumentException.class, () -> new SpatialCoverage(
        "wrong-layer",
        "wrong-file-id",
        "s3://survey/image.fits",
        8,
        0,
        CoordinateFrame.ICRS,
        HealpixNesting.NESTED,
        CoverageMethod.FITS_WCS,
        CoverageRole.FOOTPRINT,
        Modality.of("image"),
        CoveragePrecision.ESTIMATED,
        null));
  }
}
