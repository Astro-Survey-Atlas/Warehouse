# Atlas Warehouse Infrastructure

This is the self-managed infrastructure release for the new Warehouse runtime.
It owns the Elasticsearch, MinIO, and Kafka services in a dedicated namespace.
The legacy `warehouse` release and its `astro_*` indices are not referenced.

The chart deliberately does not deploy Flink or the legacy metadata-ingest
operator. The scanner writes bounded bulk requests directly to the three
`ast_*` indices.

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
atlas-warehouse-kafka.atlas-warehouse.svc.cluster.local:9092
```

The post-install hook installs strict mappings and creates, if absent,
`ast_layer_index_v1`, `ast_file_index_v1`, and `ast_coverage_index_v1`.
The hook is idempotent and never touches legacy `astro_*` indices.
