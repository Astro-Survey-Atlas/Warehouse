# Kubernetes Deployment

The new runtime is self-managed in the `atlas-warehouse` namespace. Install
the repository chart before creating ScanRequests; scanner Jobs never use the
legacy `warehouse` Services or `astro_*` indices. The old release is not a
runtime prerequisite; its five old PVs and data directories have been
released. Other retained PVs belong to unrelated namespaces and are outside
this deployment.

## Infrastructure

Create the namespace-local MinIO Secret out of band, then install the vendored
Elasticsearch, MinIO, Kafka, and `ast_*` mapping bootstrap chart:

```text
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl -n atlas-warehouse create secret generic atlas-warehouse-minio-credentials \
  --from-literal=root-user="$ATLAS_MINIO_ROOT_USER" \
  --from-literal=root-password="$ATLAS_MINIO_ROOT_PASSWORD"
helm upgrade --install atlas-warehouse ./deploy/helm/atlas-warehouse-infra \
  --namespace atlas-warehouse --create-namespace --wait --timeout 15m
```

The chart owns these stable endpoints:

```text
http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200
http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000
atlas-warehouse-kafka.atlas-warehouse.svc.cluster.local:9092
```

It creates only `ast_layer_index_v1`, `ast_file_index_v1`, and
`ast_coverage_index_v1` with strict mappings. The chart does not install Flink,
the legacy metadata operator, or any `astro_*` resource.

## Operator

## Build Images

Build the runner jars and container images from the repository root:

```text
mvn package
docker build -t astro-atlas-scanner:0.1.0 scanner-cli
docker build -t astro-atlas-operator:0.1.0 operator
```

Push both images to a registry reachable by the target cluster and set the
resulting image names in `operator-deployment.yaml` and the ScanRequest. The
Job image must contain
`/app/scanner-cli.jar`; the Operator image contains `/app/operator.jar`.

## Install

The checked-in deployment watches `ScanRequest` objects in `atlas-warehouse`.
ClusterRole intentionally does not grant Secret read access. Scanner Jobs
receive only the Secret keys referenced by the request.

```text
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl apply -f deploy/kubernetes/crd.yaml
kubectl apply -f deploy/kubernetes/rbac.yaml
kubectl apply -f deploy/kubernetes/operator-deployment.yaml
kubectl apply -f deploy/kubernetes/evidence-pvc.example.yaml
```

Create credential Secrets out of band. Values are never put in a ScanRequest:

```text
kubectl -n atlas-warehouse create secret generic atlas-source-credentials \
  --from-literal=accessKey="$ATLAS_SOURCE_ACCESS_KEY" \
  --from-literal=secretKey="$ATLAS_SOURCE_SECRET_KEY"
kubectl -n atlas-warehouse create secret generic atlas-elasticsearch-credentials \
  --from-literal=username="$ATLAS_ES_USERNAME" \
  --from-literal=password="$ATLAS_ES_PASSWORD"
```

Edit `scanrequest-oss.example.yaml` for the real endpoint, bucket, prefix, and
index endpoint. The referenced `atlas-evidence` PVC must exist in the same
namespace as the ScanRequest; the scanner writes `source-inventory.json`,
`normalized-scan.json`, and `errors.json` below the configured mount. Then
submit it:

```text
kubectl apply -f deploy/kubernetes/scanrequest-oss.example.yaml
kubectl -n atlas-warehouse get scanrequest survey-release-1 -o yaml
kubectl -n atlas-warehouse get job -l atlas.zhejianglab.org/scan-request=survey-release-1
```

The Operator creates an immutable plan ConfigMap and one scanner Job per plan
hash. A changed plan creates a new Job; the previous Job remains observable and
is eventually handled by its TTL. Delete the ScanRequest to garbage-collect
owned ConfigMaps and Jobs.

The first implementation supports one-shot scans only. It does not schedule
recurring scans, read Secret values, write Elasticsearch from reconcile, or
mount local filesystem sources. Local source Jobs need a future explicit PVC
or host-path policy rather than an implicit host mount.

Persisted scans require `spec.scanner.evidence.claimName`. The Operator mounts
that PVC at `mountPath` (default `/var/lib/atlas-evidence`) and rejects a plan
whose `evidence.outputPath` escapes the mount. An object-store-backed CSI
volume is supported through the same PVC contract; direct evidence uploads are
not part of the scanner MVP.

The resource and lifecycle contract is documented in `docs/operator.md`.

For bounded smoke workloads, create the namespace-local evidence PVC and apply
the relevant ScanRequest. The DESI examples use the MinIO bucket owned by this
chart; create `atlas-minio-desi-credentials` in `atlas-warehouse` with the
same access and secret keys as the MinIO Secret. The disposable local fixture
uses the chart Secret directly:

```text
kubectl apply -f deploy/kubernetes/evidence-pvc-minio-smoke.yaml
```

```text
kubectl apply -f deploy/kubernetes/atlas-warehouse-minio-credentials.example.yaml
kubectl apply -f deploy/kubernetes/scanrequest-minio-smoke.yaml
kubectl apply -f deploy/kubernetes/scanrequest-desi-catalog.yaml
kubectl apply -f deploy/kubernetes/scanrequest-desi-overlap.yaml
kubectl apply -f deploy/kubernetes/scanrequest-csst-oss-catalog.yaml
kubectl apply -f deploy/kubernetes/scanrequest-csst-oss-demo.yaml
```

CSST examples additionally require the existing OSS credential keys to be
created in `atlas-warehouse`. They scan bounded object keys/prefixes only;
they do not scan the Euclid multi-terabyte root or any full survey. A full
bounded product prefix should use the current scanner image, whose bulk writer
uses 100-record batches and a 90-second Elasticsearch request timeout. Keep
the Euclid `MER/` root to inventory-only and split unsupported FITS products
into explicit failed evidence rather than treating them as coverage.
