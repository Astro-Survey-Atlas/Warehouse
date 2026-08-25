# ADR-0007: Use External Elasticsearch By Default

## Status

Accepted

## Decision

The project integrates with an external Elasticsearch endpoint by default. Deployment packaging may offer a bundled single-node Elasticsearch subchart as an explicit opt-in once dedicated namespace, storage, and memory resources are available.

## Consequences

- Local and cluster tests can reuse a disposable endpoint without making a second Elasticsearch instance a hard prerequisite.
- Scanner and query modules remain independent of Elasticsearch topology and lifecycle.
- A bundled deployment must use separate names, storage, credentials, and lifecycle from the frozen legacy/reference systems.
