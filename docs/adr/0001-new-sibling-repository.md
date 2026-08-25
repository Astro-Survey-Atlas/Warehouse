# ADR-0001: Build A New Sibling Repository

## Status

Accepted

## Decision

Build Astro Survey Atlas Warehouse in `/home/aaron/Repo/Astro-Survey-Atlas-Warehouse` as an independent repository. Keep `/home/aaron/Repo/data-warehouse` frozen as a running legacy/reference system.

## Context

The existing repository has active workloads and user-staged changes. It also combines historical CRDs, execution paths, and storage contracts. An in-place redesign could affect running tasks and would force the new product to preserve abstractions that are no longer part of the desired scope.

## Consequences

- New code can establish a clean domain model and module dependency direction.
- Mature algorithms may be copied and re-tested without creating a runtime dependency on the old project.
- Migration, if ever needed, is an explicit later project rather than an accidental side effect.
- The two repositories must not share mutable generated files or deployment state.
