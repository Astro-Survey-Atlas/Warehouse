# Remote Adapters

## Elasticsearch

`index-elasticsearch` uses the JDK HTTP client and Jackson. It writes only:

- `ast_file_index_v1`
- `ast_coverage_index_v1`

Writes use deterministic document IDs and Elasticsearch bulk `index` actions. Requests are bounded by 500 records and 1.5 MB of UTF-8 NDJSON, retry transport/408/409/425/429/5xx failures up to three times, retry only failed bulk items when Elasticsearch returns item-level failures, and report sampled failed document IDs with a total count. The adapter does not create indices or rewrite mappings.

`ElasticsearchAdapter.installIndexTemplates()` installs the strict templates named `ast_file_index_v1_template` and `ast_coverage_index_v1_template`. `verifyIndexMappings()` checks both fixed indices before a deployment starts. Existing indices created with dynamic text mappings are not silently changed; rebuild or migrate them, then run verification.

Coverage search uses the requested order-8 cell set, stable sorting by explicit `keyword` source file and role fields plus cell, and an opaque Base64URL cursor containing the query-cell fingerprint and Elasticsearch `search_after` values. Reusing a cursor with another cell set is rejected.

## S3-compatible Sources

`scanner-cli` supports `s3` and `oss` source connectors through AWS SDK v2. The endpoint is supplied by the plan, credentials are resolved from environment or mounted-file references, and object URIs are canonicalized as `s3://bucket/key` or `oss://bucket/key`.

The connector may supply `region`; when omitted, the adapter uses `us-east-1`. OSS deployments should provide the region expected by the endpoint for signature compatibility.

Handlers receive a `SourceContent` seam, so FITS and catalog handlers do not know whether bytes came from local storage or object storage.

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

The modules currently build regular Maven jars; deployment packaging and a shaded distribution remain a later step.
