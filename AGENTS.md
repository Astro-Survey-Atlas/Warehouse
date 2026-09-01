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

# Agent Context

## Start Here

Read `HANDOFF.md`, `CONTEXT.md`, `docs/requirements.md`,
`docs/project-boundary.md`, and `docs/architecture.md` before changing the
project. Read the scan, index, query, and Operator contracts when changing those
interfaces.

The sibling `/home/aaron/Repo/data-warehouse` is a frozen legacy/reference
system. Never edit it or use its `astro_*` indices as a runtime fallback.

## Product Shape

Warehouse discovers astronomical files, extracts file-level sky coverage, and
maintains the current searchable state of CoverageLayers. Assets submits scans,
consumes normalized documents, owns public MOCs/evidence/reverse-lookup UX, and
connects only to the configured new Warehouse Elasticsearch endpoint.

The product is not a workflow engine, scientific reduction system, raw-data
proxy, or universal catalog. Keep v1 centered on CoverageLayer, FileAsset,
SpatialCoverage, ExtractionMode, SourceSnapshot, and ScanRequest. SourceUnit is
reserved but not implemented.

## Working Rules

- Keep `spatial-core` independent of Kubernetes, HTTP lifecycles, and
  Elasticsearch transport.
- A ScanPlan declares one ExtractionMode; callers never order internal steps.
- Validate every plan before source enumeration or credentialed I/O.
- Use ICRS and explicit NESTED HEALPix `order/ipix`; never infer finer cells
  from coarse previews or source coverage.
- Preserve coverage precision as `exact`, `estimated`, or `entrypoint-only`;
  report response truncation separately.
- Refresh one CoverageLayer as current state through `UPDATING`, `ACTIVE`, or
  `FAILED`; never expose partial or stale layer coverage as an empty result.
- Keep credentials out of plan values, evidence documents, logs, indices, and
  query responses.
- Keep `ast_*` isolated from legacy `astro_*`; index suffixes version mappings,
  not scan runs.
- Retain source inventory hashes and scan/extraction errors as evidence outside
  the browser's initial request.
- Add tests at the highest useful interface and verify behavior rather than
  private class shape.
- Update the relevant contract and ADR whenever a stable product rule changes.
- After any implementation, contract, deployment, or documentation change,
  invoke the `warehouse-validation` skill. It runs the static regression suite
  and, when a configured cluster is available, simulates Asset submissions in
  `atlas-warehouse` and Workspace submissions in `astro-data-workspace`.

## Completion Standard

A feature is complete only when its contract is documented, invalid public
inputs fail before execution, normal and failure behavior are tested, evidence
and precision invariants hold, `mvn test` passes from the repository root, and
the `warehouse-validation` result is reported. If no configured Kubernetes
cluster is available, report the caller smoke checks as skipped rather than
claiming end-to-end validation.
