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

# Warehouse Handoff

Updated: 2026-09-01

## Start Here

Read `AGENTS.md`, `CONTEXT.md`, `docs/requirements.md`,
`docs/project-boundary.md`, and `docs/architecture.md`. For an interface
change, read the matching document in `docs/` and the relevant contract tests.
The sibling `/home/aaron/Repo/data-warehouse` checkout is frozen and must not be
edited or used as a runtime fallback.

## Current Baseline

Warehouse is an astronomy-specific discovery and spatial indexing service. The
v1 domain is `CoverageLayer`, `FileAsset`, `SpatialCoverage`, `ExtractionMode`,
`SourceSnapshot`, and `ScanRequest`; `SourceUnit` is reserved and not
implemented.

- `spatial-core` owns domain types, ICRS/NESTED HEALPix rules, and ScanPlan v2 validation.
- `scanner-cli` enumerates local/S3-compatible sources, extracts file-level coverage, writes evidence, and refreshes a layer.
- `index-elasticsearch` owns strict mappings, leases, bounded bulk writes, current-layer replacement, and exact-order reads.
- `query-api` is read-only and serves diagnostic reverse lookup.
- `operator` validates namespaced requests and creates/adopts immutable plan ConfigMaps and Jobs.
- `moc-discovery-cli` performs bounded, evidence-only public MOC discovery; it never writes `ast_*` indices.

The only online indices are `ast_layer_index_v1`, `ast_file_index_v1`, and
`ast_coverage_index_v1`. Their suffix versions mappings, not scan runs. Mapping
templates are canonical under `contracts/index/`; run
`scripts/sync-index-mappings.sh --check` before committing mapping changes.

Coverage precision is `exact`, `estimated`, or `entrypoint-only`; response
truncation is reported separately. Layer refreshes move through `UPDATING`,
`ACTIVE`, or `FAILED`, and only ACTIVE layers are queryable. Credentials are
references only and never appear in plans, evidence summaries, logs, indices,
or query responses.

## Deployment

Use `deploy/helm/atlas-warehouse-infra` for Elasticsearch, MinIO, strict index
bootstrap, optional source PVCs, and the optional Kafka dependency. Use
`deploy/helm/atlas-warehouse-operator` for the Operator, MOC discovery
controller, RBAC, and both namespaced CRDs. Helm is the supported Kubernetes
installer; the default Operator values watch only `atlas-warehouse`, with
additional namespaces requiring an explicit values entry.

`deploy/compose/compose.yaml` provides the local Elasticsearch/MinIO,
query/scanner profiles, and an opt-in Kafka profile. Kafka/Flink support is
reserved for a future event-driven profile and is not required by the current
scanner path.

Credential-free Kubernetes request and PVC examples live in
`deploy/kubernetes/`. Static controller, RBAC, namespace, and CRD manifests are
intentionally not kept there. Deployment-specific endpoints, storage classes,
image registries, and Secrets belong in operator-provided values or namespace
configuration, never in reusable examples.

## Evidence And Probes

`docs/contract-probes.md` describes the probe interface and
`docs/contract-probe-results-20260825.md` records historical real-data results.
Those results are evidence of earlier runs, not current deployment state or a
public survey catalog. In particular, the historical HI4PI result predates the
current strict explicit-ICRS validation and must not be used to infer current
support.

The repeatable Kubernetes smoke baseline is documented in
[`docs/self-test.md`](docs/self-test.md) and runs from
`scripts/warehouse-smoke-test.sh`. It covers one S3 scan, one local PVC scan,
and one evidence-only MOC discovery. Cross-project submission checks run from
`scripts/warehouse-caller-smoke-test.sh`: Asset submits a ScanRequest and
MocDiscoveryRequest in `atlas-warehouse`; Workspace submits a remote
ScanRequest in `astro-data-workspace`.

## Verification

From the repository root:

```bash
mvn -B test
mvn -B verify
mvn -B -Pquality verify
helm lint deploy/helm/atlas-warehouse-infra
helm lint deploy/helm/atlas-warehouse-operator
helm template atlas-warehouse deploy/helm/atlas-warehouse-infra >/dev/null
helm template atlas-warehouse-operator deploy/helm/atlas-warehouse-operator --include-crds >/dev/null
docker compose -f deploy/compose/compose.yaml config
scripts/sync-index-mappings.sh --check
bash -n scripts/warehouse-smoke-test.sh scripts/warehouse-caller-smoke-test.sh
bash scripts/warehouse-smoke-test.sh
CALLER=asset bash scripts/warehouse-caller-smoke-test.sh
CALLER=workspace bash scripts/warehouse-caller-smoke-test.sh
```

Keep tests at the highest useful public interface. Any stable product rule
change must update its contract and ADR in the same change.
