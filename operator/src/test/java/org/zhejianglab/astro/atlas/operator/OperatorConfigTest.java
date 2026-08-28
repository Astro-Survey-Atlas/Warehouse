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

package org.zhejianglab.astro.atlas.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OperatorConfigTest {
  @Test
  void requiresAnExplicitNamespaceAllowlist() {
    assertThrows(IllegalArgumentException.class,
        () -> new OperatorConfig("", "scanner:latest", Duration.ofSeconds(10)));
    assertThrows(IllegalArgumentException.class,
        () -> new OperatorConfig("   ", "scanner:latest", Duration.ofSeconds(10)));
  }

  @Test
  void parsesDistinctNamespaceScopes() {
    OperatorConfig config = new OperatorConfig(
        "atlas-warehouse, astro-data-workspace, atlas-warehouse",
        "scanner:latest", Duration.ofSeconds(10));

    assertEquals(java.util.List.of("atlas-warehouse", "astro-data-workspace"), config.namespaces());
  }
}
