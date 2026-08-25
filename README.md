# Astro Survey Atlas Warehouse

An astronomy-specific file discovery and spatial indexing system. It finds astronomical files under S3, OSS, or a local filesystem, extracts spatial metadata, indexes file assets and HEALPix coverage in Elasticsearch, and exposes read-only spatial queries.

This repository is a new project. `/home/aaron/Repo/data-warehouse` is a frozen running legacy/reference system and is not modified by this project.

## Status

The local vertical slice and remote adapters are implemented and covered by module tests. The scanner can enumerate local files or S3-compatible objects, extract FITS/catalog coverage through a shared content seam, write the fixed Elasticsearch indices, and the query service can read coverage candidates and resolve FileAssets. A disposable OSS/Elasticsearch integration run has verified the current WCS footprint, strict mapping, stable upserts, point/cone/HEALPix queries, and cursor pagination. External Elasticsearch is the default deployment mode; a bundled instance remains optional.

The build has five runtime modules plus one shared remote adapter:

- `spatial-core`: astronomy domain types, HEALPix/WCS calculations, plan validation, index documents, and query-cell normalization.
- `index-elasticsearch`: JDK HTTP Elasticsearch writer/reader for the three fixed current-state indices.
- `scanner-cli`: local and S3-compatible source enumeration, compiled CoverageExtractor resolution, and scan execution.
- `query-api`: a small read-only HTTP service for point, cone, and HEALPix searches.
- `operator`: a thin Kubernetes adapter that creates scanner Jobs from `ScanRequest` resources and reports status. It contains no scanning logic.

## Read Next

- `HANDOFF.md`: continuation point for the next session.
- `CONTEXT.md`: canonical domain vocabulary.
- `docs/requirements.md`: product requirements and user stories.
- `docs/project-boundary.md`: ownership and non-goals.
- `docs/architecture.md`: target module structure and data flow.
- `docs/scan-plan.md`: scanner input contract.
- `docs/index-contract.md`: Elasticsearch document and spatial semantics.
- `docs/query-api.md`: query endpoints and pagination.
- `docs/operator.md`: ScanRequest, Job translation, status, and credential-reference contract.
- `docs/implementation-plan.md`: staged delivery plan and verification gates.
- `docs/adr/`: decisions that should not be rediscovered during implementation.

## Local Verification

Run the full reactor from the repository root:

```text
mvn test
mvn package
```

The local fixture and fake-index flow is exercised by `scanner-cli` and `query-api` tests. See `docs/local-vertical-slice.md` for the scope and current adapter boundaries.
