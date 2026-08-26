# ADR-0011: Own The New Warehouse Infrastructure

## Status

Accepted

## Decision

The new Warehouse runtime is deployed by this repository's vendored Helm
release in the dedicated `atlas-warehouse` namespace. The release owns
Elasticsearch, MinIO, Kafka, and strict bootstrap for the three `ast_*` indices;
it deliberately excludes Flink and the legacy metadata-ingest Operator. The
frozen `warehouse` release and `astro_*` indices remain outside the runtime path
until a separately verified migration is complete.

## Consequences

- Scanner and Assets deployments have stable new Service names and no implicit
  dependency on the legacy chart.
- Storage, credentials, evidence PVCs, and index mappings have an independent
  lifecycle and can be verified before the legacy release is removed.
- The repository carries vendored chart code and must validate its versions and
  storage settings as part of the deployment gate.
