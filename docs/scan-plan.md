# ScanPlan Contract

## Purpose

A ScanPlan describes one finite indexing run. It is the shared input for the local scanner, a Kubernetes scanner Job, and the future Operator adapter. It is not a workflow definition and cannot describe multiple dependent runs.

## Example

The example contains references to environment variables, not credential values:

```json
{
  "version": 1,
  "source": {
    "connector": {
      "type": "s3",
      "endpoint": "https://object.example.invalid",
      "credentialRef": {
        "accessKeyEnv": "ATLAS_SOURCE_ACCESS_KEY",
        "secretKeyEnv": "ATLAS_SOURCE_SECRET_KEY"
      }
    },
    "location": {
      "bucket": "survey-data",
      "prefix": "release-1/"
    }
  },
  "filters": {
    "includeSuffixes": [".fits", ".fit", ".csv", ".tsv"],
    "excludePatterns": ["**/tmp/**"]
  },
  "handlers": ["default", "fits", "coverage"],
  "modality": "image",
  "sink": {
    "connector": {
      "type": "elasticsearch",
      "endpoint": "https://search.example.invalid",
      "credentialRef": {
        "usernameEnv": "ATLAS_ES_USERNAME",
        "passwordEnv": "ATLAS_ES_PASSWORD"
      }
    }
  }
}
```

For an OSS source, `type` is `oss` and the endpoint identifies the OSS-compatible HTTP service. For a local source, the location supplies `rootPath` and the connector does not need an object-store endpoint.

## Required Semantics

- `version` is required and starts at `1`.
- `source.connector.type` is one of `s3`, `oss`, or `local` in the MVP.
- `source.connector.endpoint` is required for S3 and OSS and absent for local sources.
- `source.connector.region` is optional for local sources and should be supplied for S3-compatible services when the endpoint requires a region-specific signature.
- `source.connector.credentialRef` contains references to environment variables or mounted files. It never contains secret values.
- `source.location` supplies the bucket/prefix or local root and optional path restrictions. Location is plan data, not Connector data.
- `filters` is optional. A plan with no filter enumerates all supported file candidates under its location.
- `handlers` is an ordered non-empty list. The scanner validates every name before starting enumeration.
- `modality` is optional descriptive metadata. It is not an automatic classifier.
- `sink.connector.type` is `elasticsearch` in the MVP.
- The sink targets the fixed `ast_file_index_v1` and `ast_coverage_index_v1` contract. A plan cannot silently redirect writes to a legacy `astro_*` index.
- A plan has one source and one sink.

## Handler Pipeline

For each InputItem:

1. The scanner creates an item context with source metadata and lazy content access.
2. Handlers run in the exact order listed in `handlers`.
3. Each Handler can reuse parsed header or catalog values placed in the context by an earlier Handler.
4. Each Handler appends typed MetadataRecords; it does not write Elasticsearch directly.
5. Coverage normalization de-duplicates `(file ID, order, cell, role)` records before the writer sends them.

The initial implementation should support these behaviors:

- Default file processing: emit the FileAsset record and basic source metadata.
- FITS header/WCS processing: emit spatial evidence without reading the full image array.
- Catalog processing: read configured CSV/TSV spatial columns or HEALPix values and emit file-level spatial evidence.
- Coverage normalization: convert evidence to ICRS/NESTED order-8 cells and de-duplicate them.
- Header-only spectral handling is reserved as a future Handler behavior. Spectral array processing is not part of this contract.

The legacy names `default`, `fits`, `coverage`, and `object` may be reused where useful during extraction, but the new public contract must not reintroduce per-row object indexing. Exact handler names are finalized in `spatial-core` before scanner implementation.

## Credential Rules

- Plan JSON is safe to persist only when all credentials are references.
- Logs may identify a connector type and redacted location, but never credential values, authorization headers, or full secret file contents.
- Missing or unreadable credential references fail plan preparation before source access.

## Validation Rules

Plan validation fails before enumeration when:

- a required source location is missing;
- a source type or sink type is unsupported;
- an endpoint has an invalid scheme;
- a local path is not configured for a local source;
- no handler is selected or a handler name is unknown;
- a handler/sink combination cannot produce the fixed index contract;
- a credential reference is structurally invalid;
- a plan attempts to select legacy indices or inject raw credentials.

## Runtime Summary

The scanner emits a final summary suitable for a Kubernetes Job log and Operator status. It includes phase, discovered file count, processed item count, coverage record count, and completion time. It omits credentials and raw plan secrets.
