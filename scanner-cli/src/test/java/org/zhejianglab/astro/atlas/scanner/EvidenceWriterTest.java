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

package org.zhejianglab.astro.atlas.scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.InputItem;

class EvidenceWriterTest {
  @TempDir Path tempDir;

  @Test
  void closesAnOpenSessionAsFailedEvidence() throws Exception {
    Path root = tempDir.resolve("evidence");
    EvidenceWriter.Session session = new EvidenceWriter().start(root, "run-1", "layer-1");
    InputItem item = new InputItem("oss://bucket/catalog.csv", "catalog.csv", "oss://bucket",
        FileType.CSV, 12L, Instant.parse("2026-08-26T00:00:00Z"));
    session.record(FileAsset.from(item), List.of());
    session.phase("EXTRACTING");

    session.close();
    session.close();

    String summary = Files.readString(root.resolve("summary.json"));
    assertTrue(summary.contains("\"phase\":\"FAILED\""));
    assertTrue(summary.contains("\"errorCount\":1"));
    assertTrue(Files.readString(root.resolve("errors.json"))
        .contains("scanner process terminated before scan completed"));
    assertTrue(Files.readString(root.resolve("normalized-scan.json"))
        .contains("\"phase\":\"FAILED\""));
    assertTrue(Files.readString(root.resolve("source-inventory.json")).endsWith("]"));
  }
}
