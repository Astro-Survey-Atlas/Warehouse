# Domain Context

This glossary is semantic. Implementation details belong in the documents under `docs/`.

## FileAsset

A discovered file and its stable, queryable identity. A FileAsset describes the source URI and basic file metadata even when no spatial metadata can be extracted.

## SpatialCoverage

The association between a FileAsset and one normalized sky cell. A file can have multiple SpatialCoverage records; a query may therefore encounter the same FileAsset more than once before result de-duplication.

## Modality

The kind of astronomical data represented by a FileAsset, such as an image, catalog, or spectrum. Modality is descriptive metadata, not a promise that the system has read every scientific value in the file.

## InputItem

A source item presented to the processing pipeline. In the MVP it is normally a file-like object with a URI, size, modification time, and content access method.

## ScanPlan

The complete intent for one indexing run: source connection and location, file filters, handler order, optional modality metadata, and the output connection. A ScanPlan is finite and one-shot.

## Connector

A reusable description of how to connect to a storage or index system. A Connector supplies connection details and credentials by reference. A ScanPlan supplies the concrete source location or output location used by that run.

## Handler

An in-process processing step selected by a ScanPlan. Handlers run in declared order for one InputItem, share its context, reuse earlier parsing results, and append typed metadata records.

## MetadataRecord

A typed result produced by a Handler. File metadata and spatial coverage are distinct record kinds even when they are emitted during one file's processing.

## SpatialQuery

A read-only request for files related to a sky position, a cone around a position, or a HEALPix cell. Query matching is coverage-based and returns candidates.

## CoverageCandidate

A FileAsset returned because at least one of its SpatialCoverage cells matches a SpatialQuery. A candidate may require scientific or geometric filtering outside this MVP.

## Source Retention

The policy that indexed history remains after the source file disappears. The MVP does not reconcile storage state or create deletion tombstones.
