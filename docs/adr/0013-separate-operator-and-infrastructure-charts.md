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

# ADR-0013: Separate Operator And Infrastructure Charts

## Status

Accepted

## Decision

Warehouse publishes two Helm charts with independent lifecycles:
`atlas-warehouse-infra` owns Elasticsearch, MinIO, index bootstrap, and the
optional Kafka dependency; `atlas-warehouse-operator` owns the Operator
Deployment, ServiceAccount, and namespace-scoped RBAC. The Operator chart does
not install stateful services, and the infrastructure chart does not install
the Operator.

Users may install either chart from the OCI registry or a packaged `.tgz`.
Chart versions describe chart templates and mapping dependencies; application
image tags remain explicit values.

## Context

Stateful storage and a reconciliation control plane have different upgrade,
rollback, access, and incident boundaries. Coupling them in one release would
make a documentation or Operator change unnecessarily touch Elasticsearch,
MinIO, or evidence storage, especially while a long-running scan is active.

## Consequences

- Infrastructure can be prepared and verified before caller workloads are
  enabled.
- Operator RBAC changes are visible in a dedicated release diff.
- Rollback must select the affected chart and preserve active Jobs and PVCs.
- A complete installation guide must document both charts and their order.
