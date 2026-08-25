# Agent Context

## Start Here

Read these documents before changing the project:

1. `HANDOFF.md` for the current handoff state and next implementation step.
2. `CONTEXT.md` for the domain vocabulary.
3. `docs/requirements.md` for product requirements and user stories.
4. `docs/project-boundary.md` for what this project does and does not own.
5. `docs/architecture.md` for module ownership and dependency direction.
6. `docs/scan-plan.md`, `docs/index-contract.md`, and `docs/query-api.md` when working on those contracts.

The sibling repository `/home/aaron/Repo/data-warehouse` is a frozen running legacy/reference system. Do not edit it, migrate it in place, or alter its existing staged changes unless the user explicitly asks for that repository.

## Product Shape

This project is an astronomy-specific file discovery and spatial indexing system. It discovers files under a configured source, extracts spatial metadata from FITS headers or catalog columns, writes file and spatial-coverage documents, and answers spatial queries that return candidate files and modalities.

The project is not a general workflow engine, data catalog, ETL platform, or user-code execution platform. Keep the domain centered on `FileAsset`, `SpatialCoverage`, `ScanPlan`, `Handler`, `Connector`, and `SpatialQuery`.

The Operator is in scope as a thin Kubernetes adapter. It translates a domain scan request into a scanner Job and reports execution status. Scanner, spatial computation, and Elasticsearch I/O remain outside the reconcile loop.

## Working Rules

- Keep `spatial-core` independent of Kubernetes and HTTP server lifecycles.
- Keep scanner and query API dependencies one-way through `spatial-core`.
- Make plan validation reject unsupported source, format, handler, or sink combinations before execution.
- Keep credentials out of plan JSON, logs, indexed documents, and query responses.
- Preserve stable source-URI-derived file IDs and idempotent upsert behavior.
- Treat spatial search results as coverage candidates at HEALPix order 8; do not imply exact geometric containment.
- Add tests at the highest useful interface and verify external behavior rather than private implementation details.
- Update the relevant contract document and an ADR when a decision changes a stable product rule.

## Completion Standard

A feature is complete only when its contract is documented, its module tests cover normal and failure behavior, its public inputs reject invalid data, and `mvn test` passes from the repository root. Do not add a generic abstraction merely to make the project resemble a workflow platform.
