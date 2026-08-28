# Elasticsearch Index Contract

## Isolation And Versioning

- `ast_layer_index_v1`
- `ast_file_index_v1`
- `ast_coverage_index_v1`

These are new Warehouse mapping names. Legacy `astro_*` indices are deliberately
untouched. `v1` versions mappings, not scan runs; pre-release mapping changes use
an explicit approved rebuild of only `ast_*`.

## CoverageLayer Document

Document ID is `layer_id`. Required fields include layer/survey/release/product
identity, modality, coverage role, entrypoint, state, scan run ID, lease expiry,
source snapshot SHA-256, available orders, counts, error summary, and update
timestamps. Only `state=ACTIVE` is queryable.

## FileAsset Document

Document ID is `sha256(canonical_source_uri)`. It contains canonical URI, file
name/type, parent URI, source size, last-modified value, and latest indexed
time. It contains no raw data, modality, or layer-owned coverage list; modality
belongs to the CoverageLayer/SpatialCoverage association.

## SpatialCoverage Document

Document ID is deterministic for:

```text
(layer_id, source_file_id, healpix_order, healpix_cell, coverage_role)
```

Required fields are layer ID, source file ID/URI, actual HEALPix order/cell,
ICRS, NESTED, extraction method, coverage role, modality, precision, and optional
source order. Cells from coarse inputs remain coarse. Precision is `exact`,
`estimated`, or `entrypoint-only`; response truncation is not stored as edge
precision.

For `catalog-radec`, all implementations use the same angular conversion before
the NESTED lookup: `theta = (90 - Dec) * pi / 180` and `phi = RA * pi / 180`,
then `z = cos(theta)`. This is intentionally part of the ICRS boundary
contract: replacing it with `sin(Dec)` changes ownership of exact equatorial
boundary coordinates and makes Warehouse evidence disagree with Assets Core.

## Write Semantics

- Bulk writes are bounded by record count and UTF-8 bytes with bounded retries.
- `tryBeginLayerUpdate` uses an execution ID and expiring lease; an unexpired
  different execution receives a conflict.
- Refresh deletes coverage documents by layer ID, not whole indices.
- FileAsset upserts are global and idempotent; coverage upserts are layer-scoped.
- Failure marks the layer FAILED. Partial edges may remain physically present
  but are invisible because queries require ACTIVE state.
- Templates use `dynamic=strict`. Scanner startup never recreates indices.

## Read Semantics

A reverse lookup supplies layer IDs and one explicit order/cell set. The reader
requires every requested layer to be ACTIVE, searches matching edges at that
same order, joins unique FileAsset IDs, and returns the layer precision. It
never expands a coarse layer into finer search cells. Cursor identity includes
layer IDs, order, cells, and sort values.
