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

# Helm Deployment

Warehouse publishes two charts with separate lifecycles:

| Chart | Owns | Default namespace |
| --- | --- | --- |
| `atlas-warehouse-infra` | Elasticsearch, MinIO, strict `ast_*` bootstrap, optional Kafka | `atlas-warehouse` |
| `atlas-warehouse-operator` | Operator Deployment, ServiceAccount, and per-namespace Role/RoleBinding | `atlas-system` |

The charts do not install the legacy `warehouse` release or create/use
`astro_*` indices. The default scanner path writes bounded batches directly to
Elasticsearch and evidence storage. Kafka is disabled by default and Flink is
not installed.

## Prerequisites

- Kubernetes 1.25 or later.
- Helm 3.13 or later.
- One existing StorageClass for Elasticsearch, MinIO, and evidence PVCs.
- A registry from which the Operator, scanner, and MOC discovery images can be pulled.
- Namespace-local credential Secrets; values never belong in chart values committed to Git.

Create namespaces and the infrastructure credential Secret:

```bash
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl -n atlas-warehouse create secret generic atlas-warehouse-minio-credentials \
  --from-literal=root-user="$ATLAS_MINIO_ROOT_USER" \
  --from-literal=root-password="$ATLAS_MINIO_ROOT_PASSWORD"
```

## Install A Published Release

```bash
helm upgrade --install atlas-warehouse \
  oci://ghcr.io/astro-survey-atlas/charts/atlas-warehouse-infra \
  --version 0.1.1 --namespace atlas-warehouse --create-namespace \
  --wait --timeout 15m

helm upgrade --install atlas-warehouse-operator \
  oci://ghcr.io/astro-survey-atlas/charts/atlas-warehouse-operator \
  --version 0.1.0 --namespace atlas-system --create-namespace \
  --set 'watchNamespaces[0]=atlas-warehouse' \
  --set 'watchNamespaces[1]=astro-data-workspace' \
  --wait --timeout 10m
```

The same commands work with a downloaded `.tgz` instead of an OCI reference:

```bash
helm pull oci://ghcr.io/astro-survey-atlas/charts/atlas-warehouse-infra \
  --version 0.1.1
helm pull oci://ghcr.io/astro-survey-atlas/charts/atlas-warehouse-operator \
  --version 0.1.0
helm upgrade --install atlas-warehouse ./atlas-warehouse-infra-0.1.1.tgz \
  --namespace atlas-warehouse --wait
```

Verify the package digest published with the release before installation.

## Configure Storage And Images

For a production profile, set at least:

```yaml
global:
  storageClass: production-storage
elasticsearch:
  security:
    enabled: true
minio:
  persistence:
    size: 500Gi
```

Set immutable image tags or digests for the Operator and Job images. Do not put
access keys, passwords, or signed URLs in `values.yaml`, ScanPlan, ConfigMap,
logs, or index documents. Create source/sink Secrets in each request namespace.

## Health And Upgrade

```bash
kubectl -n atlas-warehouse get pods,pvc
kubectl -n atlas-warehouse run es-health --rm -i --restart=Never \
  --image=curlimages/curl:8.10.1 -- curl -fsS http://atlas-warehouse-elasticsearch:9200/_cluster/health
kubectl -n atlas-system rollout status deployment/atlas-warehouse-operator
helm history atlas-warehouse --namespace atlas-warehouse
```

Review `helm diff upgrade` and the live Job list before upgrading. A long scan
continues in its own Job, but changing an Operator image or chart values can
change reconciliation behavior. Never edit or delete a running Job in place.

For a release created before chart `0.1.1`, explicitly decide whether to retain
the previously enabled Kafka StatefulSet. Use `--set kafka.enabled=true` during
the upgrade when external consumers still depend on it. The current scanner
and Operator have no Kafka producer or consumer.

Rollback only the chart release that changed:

```bash
helm rollback atlas-warehouse REVISION --namespace atlas-warehouse --wait
helm rollback atlas-warehouse-operator REVISION --namespace atlas-system --wait
```

Uninstalling the chart does not automatically delete PVCs. Review evidence and
index retention before removing storage.

## Submit A Request

Install the CRDs, create an evidence PVC and namespace-local Secrets, then
apply a `ScanRequest` or `MocDiscoveryRequest`. The complete request contract
and examples are in [`../kubernetes/README.md`](../kubernetes/README.md) and
[`../../docs/operator.md`](../../docs/operator.md).

The Operator runs Jobs in the request namespace and reports status while the
caller continues other work. MOC discovery writes evidence only; it never
creates an online Warehouse layer.
