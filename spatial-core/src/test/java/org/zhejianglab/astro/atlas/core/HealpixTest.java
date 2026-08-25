package org.zhejianglab.astro.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HealpixTest {
  @Test
  void convertsCoordinatesToAValidOrderEightCell() {
    long cell = Healpix.ang2pixNest(8, 180.25, -2.5);
    assertTrue(cell >= 0 && cell < Healpix.INDEX_CELL_COUNT);
    assertEquals(cell, Healpix.ang2pixNest(8, 540.25, -2.5));
  }

  @Test
  void expandsLowerOrderQueryToOrderEightChildren() {
    assertEquals(16, Healpix.normalizeQueryCells(6, 1234).size());
    assertEquals(1, Healpix.normalizeQueryCells(10, 123456).size());
    assertEquals(1, Healpix.normalizeQueryCells(8, 123456).size());
  }

  @Test
  void coneContainsThePointCell() {
    long point = Healpix.ang2pixNest(8, 180.25, -2.5);
    assertTrue(Healpix.cellsForCone(180.25, -2.5, 0.1).contains(point));
  }
}
