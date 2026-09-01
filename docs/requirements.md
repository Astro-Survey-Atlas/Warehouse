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

# Product Requirements

## Goal

Warehouse maintains a current spatial directory of public or configured
astronomy files. Given selected survey layers and HEALPix cells, Assets can find
which known files and modalities cover those cells; users download from the
original public locations.

```text
CoverageLayer + source + ExtractionMode
  -> SourceSnapshot
  -> FileAsset + SpatialCoverage
  -> current ast_* indices
  -> layer/cell reverse lookup
```

## Functional Requirements

- A CoverageLayer is one survey, release, and product refreshed as a unit.
- A FileAsset is one discovered file with a stable canonical-URI-derived ID.
- A SpatialCoverage edge identifies layer, file, ICRS/NESTED order/ipix,
  method, role, and precision.
- Scans support local, S3-compatible, and OSS sources without copying raw
  scientific data.
- v1 ExtractionModes are `fits-wcs`, `fits-header-position`, `catalog-radec`,
  and `catalog-healpix`.
- FITS processing reads headers only. Catalog processing reads configured
  spatial columns. Scientific image, spectrum, and cube arrays are never read.
- Modality is declared from the controlled set `image`, `spectrum`, `cube`,
  `catalog`, `timeseries`, `visibility`, `event`, and `other`.
- Source inventory, hashes, filtering failures, and extraction failures are
  retained as evidence and never included in the browser's initial request.
- Extraction failures are terminal for the scan: the scanner stops at the first
  failed file (and at the first malformed catalog row), marks the layer `FAILED`,
  and never skips ahead to publish a partial result. Normal suffix filtering,
  blank lines, and catalog comments remain non-error filtering.
- Coverage keeps its actual order. Coarsening finer data is allowed; expanding
  a coarse cell and claiming finer precision is forbidden.
- Reverse lookup returns candidates, source file URI, layer identity, modality,
  order, precision, role, and official entrypoint when present.
- Response limits are explicit; a limited response reports `truncated` rather
  than silently appearing complete.

## Current-State Refresh

- Layer state is `UPDATING`, `ACTIVE`, or `FAILED`.
- Only ACTIVE layers are searchable.
- One layer cannot refresh concurrently; a lease prevents overlap and permits
  takeover after an abandoned execution expires.
- Refresh deletes the layer's old coverage edges before writing the new scan.
- A successful empty scan is an ACTIVE layer with zero coverage; a failed or
  partial scan is FAILED and never masquerades as empty coverage.
- No user-visible historical scan result is retained. Kubernetes Jobs may
  remain temporarily under TTL for operational diagnosis.
- Persisted scanner and MOC discovery Jobs use `backoffLimit=0`. A caller that
  wants to retry must
  submit a new ScanRequest/execution identity after diagnosing the evidence;
  Kubernetes must not replay a deterministic extraction failure. S3 and
  Elasticsearch adapters may perform only their documented bounded transport
  retries with request timeouts.

## Interfaces And Ownership

- ScanPlan v2 describes one source, one CoverageLayer, one ExtractionSpec, one
  index sink, and an evidence output path. Evidence is optional only in
  scanner `--memory` diagnostics; persisted Operator scans require an explicit
  evidence PVC/object-store mount.
- The Operator validates ScanPlan, creates scanner Jobs, projects credential
  references, and reports execution status. It performs no scientific parsing
  or Elasticsearch writes.
- `spatial-core` owns domain types and validation; `scanner-cli` owns source
  enumeration and extraction; `index-elasticsearch` owns persistence;
  `query-api` is a read-only diagnostic/contract surface.
- Assets production runtime reads the configured new Warehouse Elasticsearch
  directly. Query API is not a required production hop.
- New indices are `ast_file_index_v1`, `ast_coverage_index_v1`, and
  `ast_layer_index_v1`; every legacy `astro_*` index remains untouched.
- `ScanRequest` and `MocDiscoveryRequest` are namespaced resources. One Operator
  may watch multiple namespaces only through an explicit non-empty allowlist;
  an empty configuration must fail closed rather than watch the whole cluster.
- Jobs, plan ConfigMaps, evidence volumes, and credential references are created
  in the request namespace and are owned by the request there.
- `MocDiscoveryRequest` uses a fixed allowlisted policy, writes evidence only,
  and never creates a CoverageLayer or `ast_*` document.

## Controlled Non-Goals

- No SourceUnit in v1.
- No arbitrary Handler order, plugins, scripts, DAGs, or general ETL in v1.
  Flink is not part of the current scanner path, but remains an allowed future
  deployment option when an explicit event-driven contract is added.
- No per-row catalog object index.
- No exact polygon refinement promise for sampled WCS output.
- No raw-file hosting, proxy download, conversion, or scientific-array reads.
- No runtime fallback to legacy repositories or indices.

## Completion Criteria

- Plan, extraction, layer replacement, multi-order search, evidence summary,
  Operator translation, and failure paths have module tests.
- HST image WCS, SDSS spectral FITS, Gaia catalog, and HI4PI cube headers must
  be used as contract probes before release; unsupported metadata becomes
  explicit evidence.
- `mvn test` and `mvn package` pass, strict mappings verify, and a Kubernetes
  smoke scan updates only `ast_*` indices.
