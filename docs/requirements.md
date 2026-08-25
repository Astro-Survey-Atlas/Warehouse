# Product Requirements

## Status

This is the implementation handoff specification for the new repository. The legacy repository is a reference source for proven algorithms only; it is not a compatibility target. Decisions in this document are product requirements unless explicitly marked as an implementation choice.

## Problem Statement

Astronomical data is distributed across object storage and local filesystems. Most assets are FITS files, with a smaller set of CSV/TSV catalogs and spectral files. A user who knows a sky position or HEALPix region needs to discover which files and data modalities cover that area without opening every file manually.

The existing running repository combines several historical task models, execution engines, and storage paths. It is unsafe to reshape that repository while workloads are active, and its abstractions are broader than the product problem. The new project needs a narrow, astronomy-specific indexing loop with stable file identity, spatial coverage, and a read-only query surface.

## Solution

Build a standalone astronomy file discovery and spatial indexing system:

```text
source Connector + scan location
  -> enumerate files
  -> run an ordered in-process Handler pipeline
  -> emit FileAsset and SpatialCoverage records
  -> idempotently upsert the new Elasticsearch indices
  -> query by point, cone, or HEALPix cell
  -> return candidate files, modalities, and coverage metadata
```

The scanner is callable as a CLI with plan JSON. The same scanner can run locally, in a Kubernetes Job, or behind the project's thin Operator adapter. The query API is an independent read-only Java process. Neither the scanner nor the query API requires the legacy repository at runtime.

## User Stories

1. As an astronomer, I want to query a sky coordinate, so that I can discover files related to that position.
2. As an astronomer, I want to query a cone around a coordinate, so that I can discover files in a region rather than one pixel.
3. As an astronomer, I want to query a HEALPix cell, so that I can use an existing spatial partition from a survey or planning tool.
4. As an astronomer, I want returned files grouped without duplicates, so that multiple matching coverage cells do not create repeated assets.
5. As an astronomer, I want each result to include modality, so that I can distinguish images, catalogs, and spectra before opening files.
6. As a survey operator, I want to scan an S3 location, so that object-store data can be indexed without being copied into the index.
7. As a survey operator, I want to scan an OSS location, so that Alibaba-compatible object storage is supported as a first-class source.
8. As a survey operator, I want to scan a local filesystem location, so that mounted survey data can be indexed without an object-store gateway.
9. As a survey operator, I want a plan to restrict prefixes and file patterns, so that unrelated files are not processed.
10. As a survey operator, I want to select the Handler order, so that format parsing and coverage calculation happen in a predictable pipeline.
11. As a survey operator, I want a FITS scan to read headers and WCS, so that image coverage can be indexed without reading the entire image array.
12. As a survey operator, I want a catalog scan to read configured spatial columns, so that CSV/TSV files can contribute coverage.
13. As a survey operator, I want a catalog file to produce deduplicated coverage cells rather than one document per row, so that index size remains proportional to files and spatial partitions.
14. As a survey operator, I want spectral files to be enumerated and represented as assets even when scientific arrays are not read, so that their existence and spatial relationship remain discoverable.
15. As a survey operator, I want credentials referenced through environment or file configuration, so that secrets do not enter plan JSON or logs.
16. As a survey operator, I want a rescan to update the same FileAsset, so that repeated indexing is idempotent.
17. As a survey operator, I want a stable ID derived from the canonical source URI, so that file identity does not change when a scan is retried.
18. As a survey operator, I want indexed history to remain when a source file disappears, so that search results are auditable and the MVP does not silently delete data.
19. As a developer, I want spatial calculations independent of Kubernetes and HTTP lifecycles, so that they can be tested and reused by the scanner, query API, and future Operator.
20. As a developer, I want a single shared spatial and index contract, so that the scanner and query API cannot silently disagree about coordinates or document fields.
21. As a developer, I want unsupported formats, handlers, and source/sink combinations rejected during plan validation, so that failures occur before a long scan begins.
22. As a developer, I want Handler steps to share an InputItem context, so that FITS parsing is performed once and later steps can reuse it.
23. As a developer, I want Handler steps to emit typed records, so that storage writers do not need to infer scientific meaning from arbitrary maps.
24. As a developer, I want Elasticsearch writes to use bounded bulk requests and retries, so that transient transport failures do not corrupt a scan.
25. As a developer, I want the query API to use stable cursor pagination, so that large result sets do not rely on expensive deep offsets.
26. As a platform operator, I want to run the scanner as a Kubernetes Job, so that scans have isolated resources and observable completion state.
27. As a platform operator, I want a Kubernetes Operator to create scanner Jobs from an astronomy-specific scan request, so that users can submit scans through Kubernetes without putting scan logic in reconcile callbacks.
28. As a platform operator, I want Job status and scanner summaries exposed to the task status, so that failed and completed scans can be diagnosed without reading application internals.
29. As a platform operator, I want the query API to remain read-only, so that exposing it through an Ingress cannot start or mutate a scan.
30. As a maintainer, I want the new indices isolated from the legacy indices, so that running both systems cannot mix document contracts.

## Functional Requirements

### Source discovery

- The MVP supports S3, OSS, and local filesystem sources.
- Enumeration returns a source URI, file name, parent location, size, and last-modified value when the source provides it.
- The scan plan can restrict a source location and filter accepted files.
- Path names and file names cannot be used as a substitute for scientific spatial metadata.
- A file remains a FileAsset even when its spatial metadata is unknown.

### Format processing

- FITS processing reads the header and WCS needed for spatial coverage.
- CSV and TSV processing reads configured coordinate or HEALPix columns.
- CSV and TSV processing emits one FileAsset and a deduplicated set of SpatialCoverage records per file.
- The MVP does not emit one index document per catalog row.
- Spectral files are discovered and indexed as assets. Header-only spatial extraction is allowed; spectral arrays, wavelength sampling, and flux values are outside the MVP.
- Spectroscopy auto-detection is deferred. The model reserves `modality` and a header-only processing seam without promising automatic classification.

### Spatial semantics

- Coordinates use ICRS.
- Coverage uses NESTED HEALPix at fixed order 8.
- FITS coverage comes from WCS/header information.
- Catalog coverage comes from configured RA/Dec or HEALPix values.
- Point, cone, and requested HEALPix queries normalize to order-8 cells.
- Query results are coverage candidates. Pixel-boundary false positives are accepted for MVP.
- Exact spherical polygon refinement is not required for MVP.

### Indexing

- The new file index is `ast_file_index_v1`.
- The new coverage index is `ast_coverage_index_v1`.
- These indices are isolated from every legacy `astro_*` index.
- The FileAsset ID is the SHA-256 digest of the canonical source URI.
- A rescan upserts the same FileAsset ID.
- Coverage IDs are deterministic for a FileAsset, order, cell, and coverage role.
- A source file disappearing does not delete its indexed records in MVP.

### Querying

- The query API exposes point, cone, and HEALPix searches.
- Results include FileAsset identity, source URI, basic file attributes, modality, and matching coverage information.
- Results are de-duplicated by FileAsset ID.
- Default page size is 100 and the maximum is 1000.
- Pagination uses a stable sort and Elasticsearch `search_after` cursor.
- Deep `from/size` pagination is not part of the contract.
- The API is read-only and has no built-in user identity model in MVP.

### Execution and operations

- The scanner accepts plan JSON from a CLI argument or an equivalent file input.
- The plan contains non-sensitive connection and location configuration.
- Credentials are resolved from environment variables or file references and never serialized into plans or logs.
- The scanner can run as a local process or Kubernetes Job.
- The Operator, when implemented, creates a scanner Job and reports status. It does not perform spatial computation or Elasticsearch writes from a reconcile callback.
- Cron scheduling, Flink execution, and a general workflow engine are outside the initial project.

## Implementation Decisions

- The repository uses Java 17 and Maven.
- The initial repository is a Maven multi-module build with `spatial-core`, `scanner-cli`, `query-api`, and `operator`.
- `spatial-core` owns domain types, plan validation, HEALPix normalization, WCS/catalog spatial extraction contracts, index documents, and query-cell conversion.
- `scanner-cli` owns enumeration, the in-process Handler pipeline, record production, and Elasticsearch bulk writing.
- `query-api` owns HTTP parsing, validation, cursor encoding, and read-only Elasticsearch search.
- `operator` owns the `ScanRequest` Kubernetes resource, CRD/reconcile/Job translation only. Its scan input is the same canonical ScanPlan used by the scanner.
- The scanner has one source and one sink per run.
- Connector describes how to connect. A ScanPlan describes the concrete source location and output location. The MVP does not require a Connector CRD or an external Connector registry.
- Handlers are compiled into the scanner process. There is no per-Handler image, plugin loader, user script, DAG, or intermediate workflow store.
- A Handler receives an InputItem context, runs in declared order, reuses prior parsing results, and appends typed MetadataRecords.
- Record writers are separate from Handlers. The MVP writer targets the two new Elasticsearch indices. A file record writer is a future extension and does not copy raw source objects in the MVP.
- The legacy repository is mined for mature HEALPix, WCS, catalog, source enumeration, bulk retry, and test techniques by copying and adapting code into this repository. No runtime dependency or in-place migration is required.
- The HTTP implementation should stay lightweight and avoid Spring unless a later requirement justifies it. The JDK HTTP server is the default implementation option.

## Testing Decisions

Tests cross the highest useful interface and verify observable behavior. They should not assert private class structure or require a live Kubernetes cluster for core behavior.

- `spatial-core` tests cover coordinate validation, ICRS/order-8 normalization, WCS-to-cell conversion, catalog coordinate extraction, HEALPix-column extraction, deterministic IDs, plan validation, and index document shape.
- Scanner tests use local fixtures and an injectable source enumerator. They cover FITS header processing, CSV/TSV cell de-duplication, unknown spatial metadata, format filtering, Handler order, and no spectral-array reads.
- Elasticsearch writer tests use a fake transport or test HTTP server to cover bulk boundaries, retries, permanent failures, and idempotent upserts.
- Query API tests use a fake search adapter or test HTTP server to cover parameter validation, point/cone/pixel conversion, result de-duplication, cursor pagination, and read-only behavior.
- At least one integration test runs a local fixture through the scanner and queries the produced documents through the query module's search seam.
- Operator tests, when the module exists, verify Job translation, secret reference propagation without secret logging, status mapping, and failure reporting. They do not duplicate spatial algorithm tests.
- A full live S3/OSS/Elasticsearch test is optional and environment-gated. It must not be required for ordinary `mvn test`.

## Out of Scope

- General-purpose workflow, DAG, ETL, or data-catalog functionality.
- Argo, Tekton, Flink, Cron, or a generic scheduler in the initial implementation.
- Arbitrary user code, dynamic Handler plugins, or independent Handler images.
- Per-row catalog/object indexing.
- Reading spectral arrays, flux data, or wavelength samples.
- Automatic spectroscopy classification in the MVP.
- Exact polygon or pixel-boundary geometry refinement.
- Automatic source deletion reconciliation, tombstones, or index garbage collection.
- Raw object copying, conversion, or archival.
- A UI, user directory, or application-level access-control model.
- A separate spectral registration API.
- JDBC and general database sources.
- Replacing or migrating the running legacy repository in place.

## Further Notes

The exact Java HTTP implementation, public JSON field casing, Elasticsearch mapping syntax, and fixture layout are implementation choices constrained by the semantic requirements above. They should be decided in code and then recorded in the relevant contract document, rather than expanded into a new generic framework.
