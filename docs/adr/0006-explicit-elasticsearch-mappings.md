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

# ADR-0006: Use Explicit Elasticsearch Mappings

## Status

Accepted

## Decision

Publish strict composable templates for `ast_layer_index_v1`, `ast_file_index_v1`, and `ast_coverage_index_v1`, and make deployment verification explicit. The adapter must not create indices or mutate an existing incompatible mapping because dynamic field inference already caused identifier fields to become `text`, which breaks stable sorting and makes the index contract depend on document arrival order.

The template JSON is maintained once under `contracts/index/`. Java resources,
Compose mappings, and the Helm ConfigMap are synchronized copies checked by
`scripts/sync-index-mappings.sh --check`.

## Consequences

- Identifiers, URIs, categories, and query sort fields have stable `keyword` mappings.
- Undeclared fields fail early instead of silently growing the index mapping.
- Existing dynamic indices require an explicit rebuild or migration before they satisfy the new contract.
