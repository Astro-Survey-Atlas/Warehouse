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

public enum CoverageMethod {
  FITS_WCS("fits_wcs"),
  FITS_HEADER_POSITION("fits_header_position"),
  CATALOG_RADEC("catalog_radec"),
  CATALOG_HEALPIX("catalog_healpix");

  private final String value;

  CoverageMethod(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static CoverageMethod fromValue(String value) {
    for (CoverageMethod method : values()) {
      if (method.value.equals(value)) return method;
    }
    throw new IllegalArgumentException("unsupported coverage method: " + value);
  }
}
