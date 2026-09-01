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

# Warehouse Self-Test

The post-change validation gate is the `warehouse-validation` skill. It runs
the static Maven/Helm/Compose checks and then this runtime baseline plus the
caller simulations below.

The repository has one Kubernetes smoke entry point for the supported runtime
path:

```bash
bash scripts/warehouse-smoke-test.sh
```

It submits three timestamped requests in `atlas-warehouse`, waits for terminal
status, and verifies the two scan layers in `ast_layer_index_v1`:

| Check | Default input | Expected assertion |
| --- | --- | --- |
| S3 scan | MinIO `astro-artifacts/astro/smoke/assets-four-modalities/gaia-catalog-49cf5d4b.csv` | `SUCCEEDED`, one discovered file, no errors, non-empty `warehouse-selftest-s3` layer |
| Local scan | `atlas-source-catalogs:/data/gz_desi_merger_samples.csv` | `SUCCEEDED`, one discovered file, no errors, non-empty `warehouse-selftest-local` layer |
| MOC discovery | CDS policy `cds-public-moc-v2`, survey `Gaia` | `SUCCEEDED`, evidence path and candidate count; no `ast_*` writes |

The script requires an active Kubernetes context, the `ScanRequest` and
`MocDiscoveryRequest` CRDs, and these namespace-local resources:

- PVC `atlas-evidence-smoke` for evidence output;
- read-only, scanner-labelled PVC `atlas-source-catalogs` containing the local
  sample file;
- Secret `assets-atlas-minio-smoke-credentials` with `accessKey` and
  `secretKey` for the MinIO object.

The Elasticsearch and MinIO service names are the chart defaults. Override
them, or the namespace and scanner image, with environment variables when
needed:

```bash
WAREHOUSE_NAMESPACE=atlas-warehouse \
S3_SECRET_NAME=assets-atlas-minio-smoke-credentials \
ES_ENDPOINT=http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200 \
bash scripts/warehouse-smoke-test.sh
```

Every request is labelled `atlas.zhejianglab.org/self-test=warehouse-smoke`.
Requests and Jobs are retained for the Job TTL (one hour by default) so their
status and logs can be inspected. Evidence remains on the evidence PVC. Remove
only the generated request resources after review with:

```bash
kubectl -n atlas-warehouse delete scanrequest,mocdiscoveryrequest \
  -l atlas.zhejianglab.org/self-test=warehouse-smoke
```

The observed baseline on 2026-09-01 was:

| Run | Result |
| --- | --- |
| S3 | 1 file, 128 valid catalog rows, 12 coverage records, 0 errors |
| Local PVC | 1 file, 5264 valid catalog rows, 2039 coverage records, 0 errors |
| MOC | 51 candidates, response truncated as designed |

Row and coverage counts can change when the fixture changes. A passing run
requires successful terminal phases, zero scan errors, `ACTIVE` scan layers,
and at least one matching layer document; it does not require these historical
counts. The script never reads or writes legacy `astro_*` indices.

## Caller Validation

The post-change skill runs both caller paths. Run the caller smoke script
directly when diagnosing a submission contract or repeating one path:

```bash
# Asset submission: ScanRequest and MOC discovery in atlas-warehouse.
CALLER=asset KEEP_REQUESTS=1 bash scripts/warehouse-caller-smoke-test.sh

# Workspace submission: remote ScanRequest in astro-data-workspace.
CALLER=workspace KEEP_REQUESTS=1 bash scripts/warehouse-caller-smoke-test.sh
```

The requests carry the same labels used by the two callers and are created in
their respective namespaces. Asset defaults use the MinIO smoke fixture and
`atlas-evidence-smoke`. Workspace defaults use the Euclid BGMOD FITS fixture
and `workspace-evidence`; when `WORKSPACE_SOURCE_SECRET` is not set, the
script discovers the first connector Secret by the
`astro.zhejianglab.org/connector-credential=true` label and reads its
`s3-endpoint` key. Override `WORKSPACE_SOURCE_SECRET`,
`WORKSPACE_S3_ENDPOINT`, `WORKSPACE_S3_BUCKET`, and `WORKSPACE_S3_PREFIX` for a
specific Workspace connector or fixture. The script references Secret keys by
name and never prints credential values.

Successful caller checks verify that each request reaches `SUCCEEDED`, the
scanner reports a Job, evidence path, source snapshot, discovered files,
coverage, and zero errors, and the expected `ACTIVE` layer document exists.
MOC discovery reports a Job, evidence path, and candidates without creating a
scan layer. Set
`KEEP_REQUESTS=0` (the default) to remove successful request resources after
the assertions; failed requests remain for diagnosis. MOC discovery is only an
Asset path because Workspace submits remote scans, not public-catalog intent.
