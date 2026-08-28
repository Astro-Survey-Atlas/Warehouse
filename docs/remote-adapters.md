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

# Remote Adapters

## Elasticsearch

`index-elasticsearch` uses the JDK HTTP client and Jackson. It writes only:

- `ast_layer_index_v1`
- `ast_file_index_v1`
- `ast_coverage_index_v1`

Writes use deterministic document IDs and Elasticsearch bulk `index` actions. Requests are bounded by 500 records and 1.5 MB of UTF-8 NDJSON, retry transport/408/409/425/429/5xx failures up to three times, retry only failed bulk items when Elasticsearch returns item-level failures, and report sampled failed document IDs with a total count. The adapter does not create indices or rewrite mappings.

`ElasticsearchAdapter.installIndexTemplates()` installs the strict templates named `ast_layer_index_v1_template`, `ast_file_index_v1_template`, and `ast_coverage_index_v1_template`. `verifyIndexMappings()` checks all three fixed indices before a deployment starts. Existing indices created with dynamic text mappings are not silently changed; rebuild or migrate them, then run verification.

Coverage search uses one explicit requested order and cell set, stable sorting by explicit `keyword` layer/file and role fields plus cell, and an opaque Base64URL cursor containing the query-cell fingerprint and Elasticsearch `search_after` values. Reusing a cursor with another cell set is rejected.

## S3-compatible Sources

`scanner-cli` supports `s3` and `oss` source connectors through AWS SDK v2. The endpoint is supplied by the plan, credentials are resolved from environment or mounted-file references, and object URIs are canonicalized as `s3://bucket/key` or `oss://bucket/key`.

The connector may supply `region`; when omitted, the adapter uses `us-east-1`. OSS deployments should provide the region expected by the endpoint for signature compatibility.

Extraction modes receive a `SourceContent` seam, so FITS and catalog extractors do not know whether bytes came from local storage or object storage.

## Runtime Configuration

Scanner plans contain endpoint and credential references, never credential values. The query process accepts:

```text
ES_ENDPOINT   default: http://localhost:9200
ES_USERNAME   optional
ES_PASSWORD   optional
```

The scanner accepts a plan file:

```text
mvn -pl scanner-cli -am \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.zhejianglab.astro.atlas.scanner.Main \
   -Dexec.args="--plan plan.json"
```

The checked-in `docs/live-oss-plan.json` is a credential-free OSS integration plan for the supplied test prefix. It expects the two access variables to be exported from `.env` and uses the local Elasticsearch port-forward address `http://127.0.0.1:19200`.

For explicit index bootstrap, run the `index-elasticsearch` `IndexAdminMain` with `--install --recreate --verify`. `--recreate` is destructive and is intended only for a disposable test index or an approved migration window:

```text
mvn -f index-elasticsearch/pom.xml \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.zhejianglab.astro.atlas.es.IndexAdminMain \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--endpoint http://127.0.0.1:19200 --install --recreate --verify"
```

The repository deployment policy is the self-managed `atlas-warehouse` Helm
release by default for the new runtime. It supplies a dedicated single-node
Elasticsearch and does not share the frozen legacy `warehouse` release or its
`astro_*` indices. Scanner and query code still accept an explicitly supplied
external endpoint and do not depend on this topology.

The modules currently build regular Maven jars; deployment packaging and a shaded distribution remain a later step.
