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
  void followsAssetsCoreThetaConversionAtEquatorialBoundaries() {
    // The shared ICRS contract converts Dec to theta=(90-Dec) degrees before
    // evaluating HEALPix.  This exact boundary is a regression for the
    // sin(Dec) implementation, which assigned it to a different NESTED cell.
    assertEquals(1216L, Healpix.ang2pixNest(4, 0.0, 0.0));
    assertEquals(311296L, Healpix.ang2pixNest(8, 0.0, 0.0));
    assertEquals(4980736L, Healpix.ang2pixNest(10, 0.0, 0.0));
    assertEquals(289450L, Healpix.ang2pixNest(8, 1.0e-12, 0.0));
    assertEquals(300373L, Healpix.ang2pixNest(8, -1.0e-12, 0.0));
    assertEquals(311296L, Healpix.ang2pixNest(8, 360.0, 0.0));
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
