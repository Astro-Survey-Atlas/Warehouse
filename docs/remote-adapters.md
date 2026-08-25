# Remote Adapters

## Elasticsearch

`index-elasticsearch` uses the JDK HTTP client and Jackson. It writes only:

- `ast_file_index_v1`
- `ast_coverage_index_v1`

Writes use deterministic document IDs and Elasticsearch bulk `index` actions with bounded retries for transport and 5xx failures. The adapter does not create indices or mappings.

Coverage search uses the requested order-8 cell set, stable sorting by source file, cell, and role, and an opaque Base64URL cursor containing the query-cell fingerprint and Elasticsearch `search_after` values. Reusing a cursor with another cell set is rejected.

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
