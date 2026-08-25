# Project Handoff

## Starting Point

The previous repository was intentionally frozen because it has running workloads. This sibling repository is the clean implementation target:

```text
/home/aaron/Repo/Astro-Survey-Atlas-Warehouse
```

The legacy/reference repository is:

```text
/home/aaron/Repo/data-warehouse
```

The legacy repository currently contains user-staged changes. They are unrelated to this project and must remain untouched.

## Product Decision

Build an astronomy-specific file discovery and spatial indexing system, not a general data processing platform. The useful product loop is:

```text
source Connector + location
  -> file enumeration
  -> FITS header/WCS or catalog spatial extraction
  -> FileAsset and SpatialCoverage records
  -> Elasticsearch upsert
  -> point, cone, or HEALPix query
  -> candidate files and modalities
```

The MVP handles FITS, CSV/TSV catalogs, and header-only treatment of spectral files. It does not read spectral arrays, wavelength samples, or flux values. Spectroscopy auto-detection is intentionally deferred.

## Important Decisions

- The project is astronomy-specific and does not become a generic workflow or ETL platform.
- Spatial normalization is ICRS, NESTED HEALPix, fixed order 8.
- New indices are `ast_file_index_v1` and `ast_coverage_index_v1`; they are isolated from the legacy `astro_*` indices.
- File IDs are stable hashes of canonical source URIs; rescans upsert the same FileAsset.
- Coverage queries return candidates and accept order-8 pixel-boundary false positives. No exact geometry refinement is required for MVP.
- A scan has one source and one sink. The first plan JSON expresses source location and sink configuration directly; no CRD is required for the scanner itself.
- Connector represents how to connect. A plan supplies the concrete bucket, prefix, path, or output location.
- Handlers are compiled into the scanner and run in order in one process. No per-handler image, DAG, plugin system, or intermediate workflow exists.
- CSV/TSV produces one FileAsset and deduplicated coverage cells per file. It does not write one Elasticsearch object document per catalog row.
- A spectral file is still discovered and indexed as a FileAsset; MVP may derive spatial metadata from FITS headers without reading spectral arrays.
- Source deletion does not delete indexed history and does not create tombstones in MVP.
- Query API is read-only and has no built-in user identity model. Access control belongs to the surrounding Ingress/API Gateway.
- Operator remains in scope as a thin Kubernetes adapter. It will create scanner Jobs and report status; it will not calculate HEALPix, parse FITS, or write Elasticsearch from reconcile callbacks.

## Current Implementation State

The next hardening pass is implemented. FITS headers with linear TAN WCS and image dimensions produce sampled order-8 footprint cells across the image extent; files without complete geometry retain header-point coverage, while malformed spatial cards become item errors. CSV/TSV handlers accept quoted fields, explicit `catalog` column configuration, HEALPix order columns, and report valid/invalid row counts. Scan writes are accumulated into bounded batches before reaching the writer, and the Elasticsearch adapter splits requests at 500 records or 1.5 MB, retries transport and retryable item failures, and reports failed document IDs without credential material.

`index-elasticsearch` now publishes strict mapping templates, exposes install/verify/recreate operations, and provides `IndexAdminMain` for explicit bootstrap. The templates are deployment-owned and scanner startup never recreates indices. The stable mapping decision is recorded in `docs/adr/0006-explicit-elasticsearch-mappings.md`; external Elasticsearch by default is recorded in ADR-0007. QueryService now fetches additional coverage pages when duplicate coverage cells would otherwise underfill the requested unique FileAsset limit.

The `operator` Maven module is now implemented with Fabric8 Kubernetes Client. It watches namespaced `ScanRequest` resources at `atlas.zhejianglab.org/v1alpha1`, validates the canonical ScanPlan through `spatial-core`, renders a secret-free immutable plan ConfigMap, creates a plan/execution-hash-named scanner Job, projects Secret references without Secret read permission, and maps Job plus scanner summary state into CR status. Container runner jars and Kubernetes manifests are checked in under `scanner-cli`, `operator`, and `deploy/kubernetes`.

## Verification State

The local FITS/CSV vertical slice and the first live remote verification are complete. JDK 17/Maven are installed locally. The S3-compatible source adapter and Elasticsearch HTTP adapter are wired into scanner/query, and fake HTTP tests cover fixed-index writes, coverage search, cursor continuation, FileAsset lookup, explicit mappings, bulk boundaries, item retries, and permanent failures.

Live verification on 2026-08-25 used the supplied OSS endpoint and bucket/prefix through a port-forward to the disposable `warehouse` Elasticsearch Service. The two product indices were explicitly recreated, strict templates installed, and both mappings verified green before scanning. The scanner discovered and processed 6 FITS files and emitted 48 sampled order-8 WCS footprint records with zero item errors. A second scan produced the same counts (`ast_file_index_v1=6`, `ast_coverage_index_v1=48`), confirming stable-ID upsert behavior. Point, cone, and order-8 HEALPix queries returned candidates and matching coverage; point `limit=2` cursor pagination returned two pages of two unique files without duplicates; readiness returned 200; invalid coordinates returned 400 and POST returned 405. No live credentials were written to the repository, plan JSON, logs, or responses.

A local single-file catalog path is also supported. The file `/mnt/data/catalogs/cosmos-parameter-prediction/web_predictions_COSMOS_prediction_dataset.csv` was scanned in memory: 298,232 rows, valid `ra`/`dec` coordinates, one FileAsset, one known spatial status, and 19 deduplicated order-8 coverage cells. The scanner accepts either a local directory or a single local file path; `--memory` avoids Elasticsearch for this diagnostic. The live remote verification predates the new WCS footprint sampler and should be rerun after deployment templates are installed; the current shell has no live endpoint or credential references set.

The Operator was installed into the separate `atlas-system` namespace on the current k3s cluster. A real `warehouse/minio-catalog-smoke` ScanRequest created a ConfigMap and scanner Job, completed with `discovered=3 processed=3 coverage=3 catalogRows=3 catalogValid=3 catalogInvalid=0 errors=0`, and increased the disposable warehouse index counts from `6/48` to `9/51`. The temporary smoke Secret was deleted after completion; the completed CR and successful Job remain observable, while failed earlier attempts remain under their one-day TTL. The current Operator image is published to the existing private registry with tag `0.1.0-operator-fix2`.

The next implementation phase is query-api/container deployment hardening and optional Helm packaging. External Elasticsearch remains the default to avoid consuming another resident cluster; the optional bundled subchart should wait until dedicated resources are released.

## Completion Gate For This Handoff

Before implementation proceeds, the next agent should be able to answer all of these from the checked-in documents:

- What is a FileAsset and how is its ID made stable?
- What is a SpatialCoverage record and how does a query map to order-8 cells?
- Which file formats are read, and what is deliberately not read?
- Where do source locations and credentials come from?
- Which modules own spatial math, scanning, querying, and Kubernetes orchestration?
- Which existing repository is frozen and must not be edited?
