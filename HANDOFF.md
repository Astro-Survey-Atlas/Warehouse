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

The next hardening pass is now implemented locally. FITS headers with linear TAN WCS and image dimensions produce sampled order-8 footprint cells across the image extent; files without complete geometry retain header-point coverage, while malformed spatial cards become item errors. CSV/TSV handlers accept quoted fields, explicit `catalog` column configuration, HEALPix order columns, and report valid/invalid row counts. Scan writes are accumulated into bounded batches before reaching the writer, and the Elasticsearch adapter splits requests at 500 records or 1.5 MB, retries transport and retryable item failures, and reports failed document IDs without credential material.

`index-elasticsearch` now publishes strict mapping templates and exposes install/verify methods. The templates are deployment-owned and do not rewrite existing indices. The stable mapping decision is recorded in `docs/adr/0006-explicit-elasticsearch-mappings.md`.

## Verification State

The local FITS/CSV vertical slice and the first live remote verification are complete. JDK 17/Maven are installed locally. The S3-compatible source adapter and Elasticsearch HTTP adapter are wired into scanner/query, and fake HTTP tests cover fixed-index writes, coverage search, cursor continuation, FileAsset lookup, explicit mappings, bulk boundaries, item retries, and permanent failures.

Historical live verification on 2026-08-25 used the supplied OSS endpoint and bucket/prefix through the Kubernetes Elasticsearch Service port-forward. The pre-footprint-sampler scanner discovered and processed 6 FITS files and emitted 4 unique order-8 WCS coverage records. Two scans produced the same Elasticsearch counts (`ast_file_index_v1=6`, `ast_coverage_index_v1=4`), confirming stable-ID upsert behavior. Point, cone, and order-8 HEALPix queries returned the expected files and matching coverage; limit-2 cursor pagination returned a second page; health and readiness both returned 200. That run exposed dynamic `text` mappings, which motivated the explicit mapping/template decision now recorded in ADR-0006. No live credentials were written to the repository or plan JSON.

A local single-file catalog path is also supported. The file `/mnt/data/catalogs/cosmos-parameter-prediction/web_predictions_COSMOS_prediction_dataset.csv` was scanned in memory: 298,232 rows, valid `ra`/`dec` coordinates, one FileAsset, one known spatial status, and 19 deduplicated order-8 coverage cells. The scanner accepts either a local directory or a single local file path; `--memory` avoids Elasticsearch for this diagnostic. The live remote verification predates the new WCS footprint sampler and should be rerun after deployment templates are installed; the current shell has no live endpoint or credential references set.

The next environment-gated check is to install and verify the strict templates, rescan the OSS fixture with the new footprint sampler, and repeat the point/cone/HEALPix and idempotency checks. The Operator module and deployment packaging remain future phases.

## Completion Gate For This Handoff

Before implementation proceeds, the next agent should be able to answer all of these from the checked-in documents:

- What is a FileAsset and how is its ID made stable?
- What is a SpatialCoverage record and how does a query map to order-8 cells?
- Which file formats are read, and what is deliberately not read?
- Where do source locations and credentials come from?
- Which modules own spatial math, scanning, querying, and Kubernetes orchestration?
- Which existing repository is frozen and must not be edited?
