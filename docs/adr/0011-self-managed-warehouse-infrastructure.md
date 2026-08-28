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

# ADR-0011: Own The New Warehouse Infrastructure

## Status

Accepted

## Decision

The new Warehouse runtime is deployed by this repository's vendored Helm
release in the dedicated `atlas-warehouse` namespace. The release owns
Elasticsearch, MinIO, and strict bootstrap for the three `ast_*` indices;
Kafka is an optional dependency, disabled by default because the current
Scanner/Operator path does not use it. The default profile excludes Flink and
the legacy metadata-ingest Operator, but a future event-driven/Flink profile
may enable Kafka without changing the ScanPlan contract. The frozen `warehouse`
release and `astro_*` indices remain outside the runtime path until a separately
verified migration is complete.

## Consequences

- Scanner and Assets deployments have stable new Service names and no implicit
  dependency on the legacy chart or on Kafka.
- Storage, credentials, evidence PVCs, and index mappings have an independent
  lifecycle and can be verified before the legacy release is removed.
- The repository carries vendored chart code and must validate its versions and
  storage settings as part of the deployment gate.
