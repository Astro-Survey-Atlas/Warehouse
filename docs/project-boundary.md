# Project Boundary

## Purpose

Astro Survey Atlas Warehouse owns the narrow loop that turns astronomical files into spatially searchable file assets. It is a product for discovering data by sky position, not a general-purpose data platform.

## In Scope

| Area | Project responsibility |
| --- | --- |
| Domain | FileAsset, SpatialCoverage, Modality, ScanPlan, Handler, MetadataRecord, SpatialQuery |
| Source discovery | Enumerate S3, OSS, and local filesystem locations |
| Scientific extraction | FITS header/WCS and configured CSV/TSV spatial values |
| Spatial model | ICRS, NESTED HEALPix, fixed order 8 for the MVP |
| Indexing | Stable FileAsset IDs, deterministic coverage IDs, bounded Elasticsearch bulk upsert |
| Querying | Point, cone, and HEALPix candidate searches with cursor pagination |
| Runtime | Local scanner process, scanner Kubernetes Job, and a read-only query process |
| Kubernetes integration | A thin Operator that translates a scan request to a scanner Job and reports status |
| Packaging | Java 17 Maven modules and later deployment manifests for the scanner, query API, and Operator |

## Out Of Scope

| Area | Explicit non-ownership |
| --- | --- |
| Generic orchestration | No DAG, workflow engine, task marketplace, or arbitrary dependency graph |
| Generic data platform | No DataHub-like catalog governance, lineage platform, or universal metadata registry |
| User code | No arbitrary scripts, plugin marketplace, dynamic Handler loading, or per-Handler image |
| Scientific scope | No general reduction pipeline, flux processing, wavelength analysis, or full spectral ingestion |
| Storage lifecycle | No source reconciliation, deletion detection, tombstones, or garbage collection in MVP |
| Raw data movement | No object copying, conversion, or archival; indexing references source URIs |
| Catalog rows | No one-document-per-row object index in MVP |
| Infrastructure ownership | S3, OSS, Kubernetes, and ingress controls remain external infrastructure; Elasticsearch is external by default, with an optional bundled chart planned for dedicated deployments |
| Identity | No application user directory or authorization model inside the query process |
| Legacy migration | No in-place changes to `/home/aaron/Repo/data-warehouse` and no runtime compatibility promise for its historical CRDs |

## Ownership Rules

### Scanner owns processing

The scanner owns file enumeration, format detection, Handler execution, MetadataRecord production, and bulk writes. The scanner may be run by a local caller or a Kubernetes Job.

### Query API owns reading

The query API owns request validation, spatial cell conversion, Elasticsearch reads, de-duplication, and cursor pagination. It never starts a scan or mutates an index.

### Operator owns orchestration

The Operator owns Kubernetes-facing intent validation, scanner Job creation, Job observation, and task status. It does not own WCS math, catalog parsing, bulk transport, or direct Elasticsearch writes.

### Elasticsearch owns indexed search

Elasticsearch is an implementation of the read/write index target for the MVP. The domain contract is FileAsset and SpatialCoverage; the domain must not depend on Elasticsearch query syntax outside the index/query adapters.

## Boundary Tests

These checks prevent scope drift:

- If a proposed feature needs arbitrary user code, it is outside the MVP.
- If a proposed module has to schedule or coordinate multiple independent tasks, it is approaching a workflow engine and is outside the MVP.
- If a proposed Handler calls Elasticsearch directly, the seam is wrong; it should emit a typed MetadataRecord.
- If a proposed query endpoint starts a scan or changes an index, it violates the read-only query boundary.
- If a proposed task carries a secret value rather than a reference, it violates the credential boundary.
- If a proposed change modifies the running legacy repository, it violates the repository boundary.
- If a proposed format feature reads scientific arrays that are not needed for file discovery or spatial coverage, it needs an explicit scope decision.

## Relationship To The Legacy Repository

The legacy repository is a frozen reference. It contains useful prior art for HEALPix, FITS/WCS, catalog parsing, source enumeration, Elasticsearch bulk retry, and tests. Those algorithms may be copied, simplified, and tested here. The old CRDs, Helm charts, Flink paths, Kafka residence path, and historical status contracts are not dependencies of the new product.
