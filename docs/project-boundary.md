# Project Boundary

## Warehouse Owns

- ScanPlan and ScanRequest execution/status.
- S3, OSS, and local file enumeration.
- FITS-header and catalog-coordinate spatial extraction.
- Current CoverageLayer, FileAsset, and SpatialCoverage documents in `ast_*`.
- Source snapshot hashes and normalized scan/error evidence production.
- A read-only diagnostic query process.

## Assets Owns

- Survey and layer publication metadata.
- MOC, preview, query blocks, statistics, manifest, and provenance releases.
- Evidence retention policy and evidence PVC/object-store location.
- Overlap components and user-facing reverse lookup.
- Runtime configuration selecting the new Warehouse Elasticsearch endpoint.

## Explicit Non-Ownership

- Warehouse does not own raw astronomy data, user downloads, scientific array
  processing, source reduction, or archival.
- Warehouse does not provide a generic workflow engine or dynamic plugin model.
- Assets does not submit arbitrary internal extraction steps; it selects one
  declared ExtractionMode.
- Neither project modifies `/home/aaron/Repo/data-warehouse` or uses legacy
  `astro_*` as a runtime fallback.

## Module Rules

- Scanner owns enumeration, extraction, evidence production, and index writes.
- Index adapters own layer leases, current-state replacement, strict mappings,
  and multi-order searches.
- Query API owns request validation and read-only joins only.
- Operator owns Kubernetes translation and Job status only.
