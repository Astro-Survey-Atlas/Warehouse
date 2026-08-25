# Astro Survey Atlas Warehouse

An astronomy-specific file discovery and spatial indexing system. It finds astronomical files under S3, OSS, or a local filesystem, extracts spatial metadata, indexes file assets and HEALPix coverage in Elasticsearch, and exposes read-only spatial queries.

This repository is a new project. `/home/aaron/Repo/data-warehouse` is a frozen running legacy/reference system and is not modified by this project.

## Status

The local vertical slice and the first remote adapters are implemented and covered by module tests. The scanner can enumerate local files or S3-compatible objects, extract FITS/catalog coverage through a shared content seam, write the fixed Elasticsearch indices, and the query service can read coverage candidates and resolve FileAssets. A live OSS/Elasticsearch verification still requires deployment endpoints and credentials.

The build has four runtime modules plus one shared remote adapter (the Kubernetes Operator is planned but not yet a Maven module):

- `spatial-core`: astronomy domain types, HEALPix/WCS calculations, plan validation, index documents, and query-cell normalization.
- `index-elasticsearch`: JDK HTTP Elasticsearch writer/reader for the two fixed indices.
- `scanner-cli`: local and S3-compatible source enumeration, in-process Handler pipeline, and scan execution.
- `query-api`: a small read-only HTTP service for point, cone, and HEALPix searches.
- `operator`: a thin Kubernetes adapter that creates scanner Jobs and reports status. It is in product scope but contains no scanning logic.

## Read Next

- `HANDOFF.md`: continuation point for the next session.
- `CONTEXT.md`: canonical domain vocabulary.
- `docs/requirements.md`: product requirements and user stories.
- `docs/project-boundary.md`: ownership and non-goals.
- `docs/architecture.md`: target module structure and data flow.
- `docs/scan-plan.md`: scanner input contract.
- `docs/index-contract.md`: Elasticsearch document and spatial semantics.
- `docs/query-api.md`: query endpoints and pagination.
- `docs/implementation-plan.md`: staged delivery plan and verification gates.
- `docs/adr/`: decisions that should not be rediscovered during implementation.

## Local Verification

Run the full reactor from the repository root:

```text
mvn test
mvn package
```

The local fixture and fake-index flow is exercised by `scanner-cli` and `query-api` tests. See `docs/local-vertical-slice.md` for the scope and current adapter boundaries.
