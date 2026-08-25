# ADR-0006: Use Explicit Elasticsearch Mappings

## Status

Accepted

## Decision

Publish strict composable templates for `ast_layer_index_v1`, `ast_file_index_v1`, and `ast_coverage_index_v1`, and make deployment verification explicit. The adapter must not create indices or mutate an existing incompatible mapping because dynamic field inference already caused identifier fields to become `text`, which breaks stable sorting and makes the index contract depend on document arrival order.

## Consequences

- Identifiers, URIs, categories, and query sort fields have stable `keyword` mappings.
- Undeclared fields fail early instead of silently growing the index mapping.
- Existing dynamic indices require an explicit rebuild or migration before they satisfy the new contract.
