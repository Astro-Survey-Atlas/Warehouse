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

# Atlas Warehouse Infrastructure Chart

This chart owns the self-managed Warehouse dependencies in one namespace:
Elasticsearch, MinIO, and strict bootstrap for
`ast_layer_index_v1`, `ast_file_index_v1`, and `ast_coverage_index_v1`.

It does not install the Operator, Flink, the legacy metadata operator, or the
legacy `warehouse` release. Kafka is a chart dependency but is disabled by
default because Scanner and Operator currently write directly to Elasticsearch
and evidence storage.

## Install

Create the namespace-local MinIO Secret first:

```bash
kubectl create namespace atlas-warehouse
kubectl -n atlas-warehouse create secret generic atlas-warehouse-minio-credentials \
  --from-literal=root-user="$ATLAS_MINIO_ROOT_USER" \
  --from-literal=root-password="$ATLAS_MINIO_ROOT_PASSWORD"

helm upgrade --install atlas-warehouse . \
  --namespace atlas-warehouse --wait --timeout 15m
```

For OCI and `.tgz` installation, see [`../README.md`](../README.md).

## Endpoints

```text
http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200
http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000
```

When explicitly enabled, Kafka is available at:

```text
atlas-warehouse-kafka.atlas-warehouse.svc.cluster.local:9092
```

## Values That Matter

| Value | Default | Meaning |
| --- | --- | --- |
| `global.storageClass` | `""` | StorageClass for bundled stateful services; empty uses the cluster default |
| `elasticsearch.master.persistence.size` | `20Gi` | Elasticsearch data volume |
| `minio.persistence.size` | `50Gi` | Evidence/object data volume |
| `indexBootstrap.enabled` | `true` | Install strict templates and create missing `ast_*` indices |
| `kafka.enabled` | `false` | Opt-in broker for a future event-driven/Flink profile |
| `sourceVolumes` | `[]` | Optional namespace-local, read-only scanner input PVCs |

Use immutable image tags or digests in production and enable authentication,
TLS, backups, resource limits, and a suitable StorageClass. The bundled
single-node Elasticsearch and standalone MinIO values are for validation, not
high availability.

### Scanner source volumes

The chart can own an NFS-backed static PV/PVC for local scans. Configure this
only with deployment-specific values; the source export is never included in a
ScanRequest or Assets Connector:

```yaml
sourceVolumes:
  - claimName: atlas-source-catalogs
    capacity: 1800Gi
    accessModes: [ReadOnlyMany]
    nfs:
      server: nfs.example.invalid
      path: /exports/catalogs
```

To use a claim created outside this chart, set `existingClaim: true` and add
`atlas.zhejianglab.org/scanner-source=true` to the claim. The Operator mounts
only labelled, `Bound` claims read-only and validates that the request's local
path stays under its mount. PVCs are namespace-local, so a watched Workspace
namespace needs its own approved claim if it submits local scans.

## Upgrade Safety

Before changing this release, inspect active ScanRequest Jobs and run a Helm
diff. Chart `0.1.1` changed Kafka from an unconditional dependency to an
optional, disabled-by-default dependency. Preserve an existing broker with
`--set kafka.enabled=true` until external consumers have migrated. The index
bootstrap hook is idempotent and never touches legacy `astro_*` indices.

## Uninstall And Data

```bash
helm uninstall atlas-warehouse --namespace atlas-warehouse
```

PVCs are retained according to the cluster policy. Removing them is a separate,
destructive data-retention decision. Evidence and raw source data are not
recreated by reinstalling this chart.
