# Kubernetes Operator

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

The deployment watches `ScanRequest` objects in all namespaces by default. The
ClusterRole intentionally does not grant Secret read access. Scanner Jobs
receive only the Secret keys referenced by the request.

```text
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl apply -f deploy/kubernetes/crd.yaml
kubectl apply -f deploy/kubernetes/rbac.yaml
kubectl apply -f deploy/kubernetes/operator-deployment.yaml
```

Create credential Secrets out of band. Values are never put in a ScanRequest:

```text
kubectl -n atlas create secret generic atlas-source-credentials \
  --from-literal=accessKey="$ATLAS_SOURCE_ACCESS_KEY" \
  --from-literal=secretKey="$ATLAS_SOURCE_SECRET_KEY"
kubectl -n atlas create secret generic atlas-elasticsearch-credentials \
  --from-literal=username="$ATLAS_ES_USERNAME" \
  --from-literal=password="$ATLAS_ES_PASSWORD"
```

Edit `scanrequest-oss.example.yaml` for the real endpoint, bucket, prefix, and
index endpoint, then submit it:

```text
kubectl apply -f deploy/kubernetes/scanrequest-oss.example.yaml
kubectl -n atlas get scanrequest survey-release-1 -o yaml
kubectl -n atlas get job -l atlas.zhejianglab.org/scan-request=survey-release-1
```

The Operator creates an immutable plan ConfigMap and one scanner Job per plan
hash. A changed plan creates a new Job; the previous Job remains observable and
is eventually handled by its TTL. Delete the ScanRequest to garbage-collect
owned ConfigMaps and Jobs.

The first implementation supports one-shot scans only. It does not schedule
recurring scans, read Secret values, write Elasticsearch from reconcile, or
mount local filesystem sources. Local source Jobs need a future explicit PVC
or host-path policy rather than an implicit host mount.

The resource and lifecycle contract is documented in `docs/operator.md`.

For the disposable in-cluster MinIO fixture, submit
`scanrequest-minio-smoke.yaml`. It uses `atlas-minio-smoke-credentials` by key
reference in the checked-in example; create that temporary Secret from your
fixture credentials before submission:

```text
kubectl -n warehouse create secret generic atlas-minio-smoke-credentials \
  --from-literal=accessKey="$MINIO_ACCESS_KEY" \
  --from-literal=secretKey="$MINIO_SECRET_KEY"
```

It scans
`astro-artifacts/astro/smoke/`; it is intentionally tied to the existing
`warehouse` test namespace and is not a production deployment.
