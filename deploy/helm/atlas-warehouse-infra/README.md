# Atlas Warehouse Infrastructure

This is the self-managed infrastructure release for the new Warehouse runtime.
It owns Elasticsearch and MinIO services in a dedicated namespace. Kafka is an
optional chart dependency and is disabled by default because the current
Scanner/Operator path writes directly to Elasticsearch and evidence storage.
The legacy `warehouse` release and its `astro_*` indices are not referenced.

The chart does not deploy Flink or the legacy metadata-ingest operator in the
default profile. The scanner writes bounded bulk requests directly to the three
`ast_*` indices. A future Flink/event-driven profile may enable Kafka without
changing the current ScanPlan contract.

## Install

Create the MinIO credential Secret in the target namespace before installing:

```text
kubectl -n atlas-warehouse create secret generic atlas-warehouse-minio-credentials \
  --from-literal=root-user="$ATLAS_MINIO_ACCESS_KEY" \
  --from-literal=root-password="$ATLAS_MINIO_SECRET_KEY"
helm upgrade --install atlas-warehouse ./deploy/helm/atlas-warehouse-infra \
  --namespace atlas-warehouse --create-namespace
```

The stable in-cluster endpoints are:

```text
http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200
http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000
```

When Kafka is enabled, its endpoint is:

```text
atlas-warehouse-kafka.atlas-warehouse.svc.cluster.local:9092
```

Enable it explicitly for an event-driven or future Flink deployment:

```text
helm upgrade --install atlas-warehouse ./deploy/helm/atlas-warehouse-infra \
  --namespace atlas-warehouse --create-namespace \
  --set kafka.enabled=true
```

Do not enable Kafka solely for the current scanner/operator path; it has no
producer or consumer in this release.

When upgrading a release created before chart `0.1.1`, review the Kafka
decision explicitly. The new default is disabled; pass `--set kafka.enabled=true`
to retain the existing broker, or leave it disabled only after confirming no
external workload depends on that Kafka service.

The post-install hook installs strict mappings and creates, if absent,
`ast_layer_index_v1`, `ast_file_index_v1`, and `ast_coverage_index_v1`.
The hook is idempotent and never touches legacy `astro_*` indices.
