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

public final class QueryLimits {
  public static final int DEFAULT = 100;
  public static final int MAXIMUM = 1000;

  private QueryLimits() {}

  public static void validate(int limit, String cursor) {
    if (limit < 1 || limit > MAXIMUM) throw new IllegalArgumentException("limit must be between 1 and 1000");
    if (cursor != null && cursor.isBlank()) throw new IllegalArgumentException("cursor must not be blank");
  }
}
