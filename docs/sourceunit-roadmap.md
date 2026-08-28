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

# SourceUnit Roadmap

`SourceUnit` is reserved and intentionally absent from the v1 CRD, ScanPlan,
Elasticsearch mappings, query responses, and public release contract.

## Why It May Be Needed

One logical astronomy source can contain many `FileAsset` records: an
observation, tile, exposure, processing product, or catalog shard may have
multiple files and versions. A future SourceUnit could provide the stable
grouping needed for:

- lineage from an observation or product to its files;
- duplicate-file consolidation across releases;
- tile/exposure/product organization;
- user-facing download plans that select a logical source rather than a single file.

## Entry Criteria

Do not add SourceUnit because a UI needs another label. Before implementation,
the project must have a concrete use case and an approved contract for:

1. identity and ownership;
2. lifecycle and refresh semantics;
3. relationship to CoverageLayer, FileAsset, and SpatialCoverage;
4. query and pagination behavior;
5. provenance, deduplication, and backward compatibility.

The first proposal should be recorded as an ADR and tested against at least one
multi-file observation and one duplicate-file scenario. Until then, a
`CoverageLayer` plus FileAsset associations remains the complete v1 model.
