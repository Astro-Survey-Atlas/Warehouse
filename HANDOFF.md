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

## Next Implementation Step

The local FITS/CSV vertical slice and the first live remote verification are complete. JDK 17/Maven are installed locally. The S3-compatible source adapter and Elasticsearch HTTP adapter are wired into scanner/query, and fake HTTP tests cover fixed-index writes, coverage search, cursor continuation, and FileAsset lookup.

Live verification on 2026-08-25 used the supplied OSS endpoint and bucket/prefix through the Kubernetes Elasticsearch Service port-forward. The scanner discovered and processed 6 FITS files and emitted 4 unique order-8 WCS coverage records. Two scans produced the same Elasticsearch counts (`ast_file_index_v1=6`, `ast_coverage_index_v1=4`), confirming stable-ID upsert behavior. Point, cone, and order-8 HEALPix queries returned the expected files and matching coverage; limit-2 cursor pagination returned a second page; health and readiness both returned 200. The first live read exposed dynamic `text` mappings, so the adapter was corrected to sort on `.keyword` fields; the rerun passed. No live credentials were written to the repository or plan JSON.

A local single-file catalog path is also supported. The file `/mnt/data/catalogs/cosmos-parameter-prediction/web_predictions_COSMOS_prediction_dataset.csv` was scanned in memory: 298,232 rows, valid `ra`/`dec` coordinates, one FileAsset, one known spatial status, and 19 deduplicated order-8 coverage cells. The scanner accepts either a local directory or a single local file path; `--memory` avoids Elasticsearch for this diagnostic.

## Completion Gate For This Handoff

Before implementation proceeds, the next agent should be able to answer all of these from the checked-in documents:

- What is a FileAsset and how is its ID made stable?
- What is a SpatialCoverage record and how does a query map to order-8 cells?
- Which file formats are read, and what is deliberately not read?
- Where do source locations and credentials come from?
- Which modules own spatial math, scanning, querying, and Kubernetes orchestration?
- Which existing repository is frozen and must not be edited?
