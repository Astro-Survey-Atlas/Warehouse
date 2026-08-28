<!--
Copyright 2026 Astro Survey Atlas contributors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Local Vertical Slice

The first executable slice is intentionally local and dependency-free:

```text
LocalSourceAdapter
  -> ScanService and one declared ExtractionMode
  -> InMemoryIndex
  -> QueryService
  -> QueryHttpServer
```

`scanner-cli` tests create FITS header fixtures, a CSV catalog with duplicate coordinates, and an ignored text file. Each scan declares one ExtractionMode and writes one FileAsset per supported input plus one SpatialCoverage per unique explicit cell. `query-api` tests exercise the ACTIVE-layer coverage-to-file join and send real HTTP requests to the JDK server for health, readiness, v2 HEALPix search, and invalid-query responses.

Run the complete verification from the repository root:

```text
mvn test
mvn package
```

The current `scanner-cli` `Main` accepts `--plan <path>`, selects local or S3-compatible source access from the plan, and writes through the Elasticsearch adapter. Add `--memory` to run the complete scan without Elasticsearch and print layer state/cell summaries. A local source path may be either a directory or one file. The in-memory index remains available to tests; live execution requires the endpoint and credential references in the plan.

The fixed production boundaries are already represented by `IndexWriter` and `IndexReader`. S3/OSS and Elasticsearch adapters use those seams, write only `ast_layer_index_v1`, `ast_file_index_v1`, and `ast_coverage_index_v1`, and resolve credentials from references rather than plan values.

For the local CSV-only check:

```text
java -Xmx1g -cp "scanner-cli/target/classes:<dependency-classpath>" \
  org.zhejianglab.astro.atlas.scanner.Main \
  --plan /tmp/cosmos-local-file-plan.json --memory
```

The `--memory` mode is intentionally not a persistence path; it is for validating enumeration, format parsing, and coverage generation without mutating Elasticsearch.
