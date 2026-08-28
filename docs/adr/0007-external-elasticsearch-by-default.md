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

# ADR-0007: Use External Elasticsearch By Default

## Status

Superseded by ADR-0011

## Decision

The project integrates with an external Elasticsearch endpoint by default. Deployment packaging may offer a bundled single-node Elasticsearch subchart as an explicit opt-in once dedicated namespace, storage, and memory resources are available.

## Consequences

- Local and cluster tests can reuse a disposable endpoint without making a second Elasticsearch instance a hard prerequisite.
- Scanner and query modules remain independent of Elasticsearch topology and lifecycle.
- A bundled deployment must use separate names, storage, credentials, and lifecycle from the frozen legacy/reference systems.
