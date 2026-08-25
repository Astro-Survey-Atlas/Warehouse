# Elasticsearch Index Contract

## Version And Isolation

The MVP writes exactly these new indices:

- `ast_file_index_v1`
- `ast_coverage_index_v1`

They are intentionally separate from the legacy repository's `astro_*` indices. The new scanner must not write the legacy contract, and the query API must not read it as a fallback.

## Coordinate Contract

- Coordinate frame: ICRS.
- Pixelization: HEALPix.
- Nesting: NESTED.
- Index order: 8.
- `healpix_cell` is an order-8 integer cell identifier.
- Queries at any supported input order normalize to the order-8 representation before search.

The index is a candidate index. A matching cell proves that the file's indexed coverage overlaps the query cell at the chosen resolution; it does not prove exact geometric containment.

## FileAsset Document

There is one FileAsset document for every discovered file, including files whose spatial status is unknown.

The document ID is:

```text
sha256(canonical_source_uri)
```

The canonicalization algorithm is part of `spatial-core` and must be deterministic and covered by tests. The source URI, not file content or scan time, is the identity key.

Required semantic fields:

| Field | Meaning | Suggested mapping |
| --- | --- | --- |
| `file_id` | Stable source identity, equal to the document ID | keyword |
| `source_uri` | URI used to locate the source file | keyword |
| `file_name` | Final path/name segment | keyword |
| `parent_uri` | Parent source location | keyword |
| `file_type` | Detected file type such as FITS, CSV, or TSV | keyword |
| `size_bytes` | Source-reported size when available | long |
| `last_modified` | Source-reported modification time when available | date |
| `modality` | Optional descriptive modality | keyword |
| `spatial_status` | `known`, `unknown`, or `error` | keyword |
| `coverage_cells` | Optional list of order-8 cells known for this file | integer array |
| `indexed_at` | Time of this successful upsert | date |

The implementation may add survey, release, product, or provenance fields when they are supplied by the plan, but arbitrary user maps are not part of the MVP contract. New fields need an explicit mapping decision to avoid uncontrolled mapping growth.

## SpatialCoverage Document

There is one document for each unique coverage cell associated with a FileAsset and coverage role.

The coverage document ID is deterministic for:

```text
(file_id, healpix_order, healpix_cell, coverage_role)
```

Required semantic fields:

| Field | Meaning | Suggested mapping |
| --- | --- | --- |
| `source_file_id` | FileAsset document ID | keyword |
| `source_uri` | Denormalized source URI for diagnostics | keyword |
| `healpix_order` | Always 8 in the MVP | integer |
| `healpix_cell` | NESTED order-8 cell | long |
| `coordinate_frame` | Always ICRS in the MVP | keyword |
| `nesting` | Always NESTED in the MVP | keyword |
| `coverage_method` | WCS, catalog coordinates, or catalog HEALPix | keyword |
| `coverage_role` | Footprint, occupancy, or a later typed role | keyword |
| `modality` | Denormalized modality when known | keyword |
| `quality` | Optional extraction quality indicator | keyword |

Coverage records are de-duplicated before writing. A CSV/TSV with many rows in one cell produces one coverage document for that cell, not one per row.

## Extraction Rules

- FITS coverage comes from WCS/header information. The current header-only implementation rasterizes linear TAN image WCS using `CD` or `CDELT` plus optional `CROTA2`; incomplete geometry may use a header center point, while malformed or unsupported geometry is reported as extraction error.
- CSV/TSV coverage comes from configured RA/Dec or NESTED HEALPix values.
- A file with no usable spatial evidence receives a FileAsset with `spatial_status=unknown` and no coverage documents.
- A malformed spatial value is reported as extraction error according to scan policy and is never guessed from a path or file name.
- Spectral arrays are not read for the MVP. Header-only spatial evidence is acceptable.

## Write Semantics

- FileAsset and SpatialCoverage writes are idempotent upserts.
- Bulk requests are bounded by both byte size and record count.
- Transient transport failures are retried with a bounded policy; permanent failures fail the scan.
- The writer does not delete documents because a source item is absent from a later scan.
- The Elasticsearch adapter exposes explicit composable templates for both fixed indices. They use `dynamic=strict`, map identifiers and categorical fields as `keyword`, dates as `date`, sizes and HEALPix cells as numeric fields, and reject undeclared fields.
- Template installation is deployment-owned and does not create indices or rewrite an existing mapping. The adapter can verify that both existing indices contain the required strict mapping; an incompatible index must be rebuilt or migrated explicitly before scanning.
- `recreateFixedIndices()` is an explicit index-admin operation for disposable integration environments or approved migrations. It is never invoked by scanner startup.

## Query Join

The query API first finds matching SpatialCoverage documents, then resolves their `source_file_id` values to FileAsset documents. It de-duplicates IDs before returning results. A FileAsset can be returned once even when several coverage cells match the same query.
