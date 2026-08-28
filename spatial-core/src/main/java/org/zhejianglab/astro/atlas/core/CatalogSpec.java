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

/** Optional column overrides for CSV and TSV spatial extraction. */
public record CatalogSpec(
    String raColumn,
    String decColumn,
    String healpixColumn,
    String healpixOrderColumn,
    Integer healpixOrder) {
  public CatalogSpec {
    raColumn = normalize(raColumn);
    decColumn = normalize(decColumn);
    healpixColumn = normalize(healpixColumn);
    healpixOrderColumn = normalize(healpixOrderColumn);
  }

  public static CatalogSpec empty() {
    return new CatalogSpec(null, null, null, null, null);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
