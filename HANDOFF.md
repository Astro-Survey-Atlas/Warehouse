# Warehouse Session Handoff

Updated: 2026-08-26

Repository: `/home/aaron/Repo/Astro-Survey-Atlas-Warehouse`

Starting commit: `3f7df52`

## Start Here

Read `AGENTS.md`, `CONTEXT.md`, `docs/requirements.md`,
`docs/project-boundary.md`, `docs/architecture.md`, and the contract relevant
to the code being changed. The Assets integration handoff is
`/home/aaron/Repo/Astro-Survey-Atlas-Assets/HANDOFF.md`.

This repository has substantial uncommitted implementation, documentation,
probe, and deployment work. At this checkpoint `git status --short` is:

```text
 M HANDOFF.md
 M deploy/kubernetes/evidence-pvc-minio-smoke.yaml
 M deploy/kubernetes/operator-deployment.yaml
 M docs/contract-probes.md
 M docs/implementation-plan.md
 M index-elasticsearch/src/main/java/org/zhejianglab/astro/atlas/es/ElasticsearchAdapter.java
 M operator/src/main/java/org/zhejianglab/astro/atlas/operator/ScanRequestSpecParser.java
 M operator/src/test/java/org/zhejianglab/astro/atlas/operator/ScanRequestSpecParserTest.java
 M scanner-cli/src/main/java/org/zhejianglab/astro/atlas/scanner/CatalogHandler.java
 M scanner-cli/src/main/java/org/zhejianglab/astro/atlas/scanner/ScanService.java
 M scanner-cli/src/test/java/org/zhejianglab/astro/atlas/scanner/LocalScanTest.java
 M spatial-core/src/main/java/org/zhejianglab/astro/atlas/core/FileType.java
?? deploy/kubernetes/scanrequest-csst-oss-catalog.yaml
?? deploy/kubernetes/scanrequest-csst-oss-demo.yaml
?? deploy/kubernetes/scanrequest-desi-catalog.yaml
?? deploy/kubernetes/scanrequest-desi-overlap.yaml
?? docs/contract-probe-results-20260825.md
```

These files are the current implementation, not disposable generated output.
Review and preserve them. The sibling `/home/aaron/Repo/data-warehouse` is a
frozen legacy/reference repository and must remain untouched.

## Fixed Product Decisions

- Warehouse is an astronomy-specific discovery and spatial indexing service,
  not a general workflow engine, reduction pipeline, download proxy, or
  universal catalog.
- The current domain is CoverageLayer, FileAsset, SpatialCoverage,
  ExtractionMode, SourceSnapshot, and ScanRequest. FileAsset is implemented;
  SourceUnit remains reserved until data justifies it.
- A source refresh replaces current layer state through `UPDATING`, `ACTIVE`,
  or `FAILED`. Only ACTIVE is queryable. There is no user-queryable scan history.
- Indices are `ast_layer_index_v1`, `ast_file_index_v1`, and
  `ast_coverage_index_v1`. Their suffix versions mappings/contracts, not runs.
  Isolation from the old `astro_*` indices is intentional.
- FileAsset IDs are stable hashes of canonical source URIs. Shared FileAssets
  may remain while a layer's coverage edges are replaced.
- SpatialCoverage uses ICRS and explicit NESTED HEALPix `order/ipix`, retaining
  `exact`, `estimated`, or `entrypoint-only` precision.
- V1 extraction modes are `fits-wcs`, `fits-header-position`, `catalog-radec`,
  and `catalog-healpix`. One typed mode is declared per plan; extractor order is
  compiled inside Warehouse rather than supplied as a Handler pipeline.
- CSV/TSV catalogs create one FileAsset per file and deduplicated coverage
  cells, not one Elasticsearch document per catalog row.
- The thin Operator validates ScanPlan v2, creates scanner Jobs, and reports
  status. Spatial extraction and Elasticsearch writes stay in the scanner.
- Credentials are references only. Evidence retains inventory hashes and
  extraction errors outside browser startup payloads.

## Current Implementation

The multi-module Java implementation contains spatial contracts, scanner CLI,
local and S3-compatible connectors, strict Elasticsearch mappings and query
adapter, HTTP query API, and a Fabric8 Kubernetes Operator. ScanPlan v2 supports
local directory/single-file sources and S3/OSS bucket-prefix sources. Evidence
includes inventory, errors, and a normalized scan document.

FITS support reads headers rather than scientific arrays. Linear TAN image WCS
is sampled into estimated cells; explicit header coordinates produce an
entrypoint-only cell. CSV/TSV supports configured RA/Dec or NESTED HEALPix
columns. HST WCS in a later `SCI` HDU and FITS binary-table catalogs are
currently unsupported and must fail visibly rather than invent coverage.

The implementation is a functioning vertical slice, but it is not yet safe for
large authoritative refreshes because of the correctness and streaming gaps
listed below.

## Live Deployment Layout

The current cluster runs the Warehouse infrastructure as Helm release
`warehouse` in namespace `warehouse`, chart `metadata-environment-0.0.32`
(application version `1.11.0`). That release provides the Elasticsearch,
Kafka, MinIO, Flink session cluster, and metadata-ingest components. The
Elasticsearch Service is:

```text
http://warehouse-elasticsearch.warehouse.svc.cluster.local:9200
```

The new Warehouse scanner/operator code is deployed separately. The Operator
runs as Deployment `astro-atlas-operator` in namespace `atlas-system`, using
the image `astro-atlas-operator:0.2.0-20260825-fix2`. It watches namespaced
`ScanRequest` resources, creates immutable plan ConfigMaps and one scanner Job
per rendered plan, and injects the scanner image
`astro-atlas-scanner:0.2.0-20260825-fix3`. Scanner Jobs run in the ScanRequest
namespace, read OSS/S3/MinIO through Secret references, write the three
`ast_*` indices, and persist inventory/normalized/error evidence to the
namespace-local `atlas-evidence-smoke` PVC.

The checked-in installation sequence is: build Maven jars, build and push the
scanner/operator images, apply `namespace.yaml`, `crd.yaml`, `rbac.yaml`, and
`operator-deployment.yaml`, create an evidence PVC and credential Secrets, then
apply a `ScanRequest` manifest. See `deploy/kubernetes/README.md`; the
CSST/DESI examples under `deploy/kubernetes/scanrequest-*.yaml` are bounded
smoke workloads, not full-survey scans.

## Verification Baseline

The last local gate was green:

```text
mvn test                         # 53 tests passed
mvn package -DskipTests          # passed
kubectl create --dry-run=client  # checked manifests parsed
git diff --check                 # clean at the reviewed checkpoint
```

Recorded probes on 2026-08-25:

- A v2 OSS ScanRequest read one Euclid VIS FITS file, produced 11 estimated
  order-8 cells, and reported zero errors.
- A bounded Euclid tile inventory found 44 FITS objects (50.976 GiB). Four
  exact-key VIS/NISP/DECAM image probes each produced 11 estimated order-8 cells.
- The Euclid `MER/` root inventory listed 15,948 FITS objects across 352 tile
  prefixes, about 19 TiB total. It was inventoried only, never bulk-downloaded.
- Gaia DR3 CSV produced 12 exact order-8 cells from 128 valid rows. An explicit
  HEALPix catalog preserved three source order-8 cells.
- SDSS spectral FITS produced one entrypoint-only order-8 cell. HI4PI's 3D cube
  produced 7,494 estimated spatial cells while ignoring the spectral axis.
- HST multi-HDU WCS and two Euclid FITS binary-table catalogs produced explicit
  unsupported/error results with no fabricated coverage.
- Assets read ACTIVE CSST, DESI, and Euclid layers from the `ast_*` indices and
  completed catalog, overlap, and file reverse-lookup requests.

The live `ast_*` counts on 2026-08-26 are 5 layer documents, 5 FileAssets, and
2,060 coverage edges. ACTIVE edges are split across CSST catalog (5), DESI
merger catalog (2,039), DESI overlap catalog (5), and Euclid VIS (11). The
five indexed files total 1,652,927,417 bytes (about 1.54 GiB); successful
catalog probes contain 26,134 valid rows. The current CSST image layer is
`FAILED` with one missing-spatial-header error, so it contributes no queryable
coverage. The Euclid `MER/` root remains inventory-only: 15,948 FITS objects,
about 19 TiB, were listed but not bulk-scanned. The local Gaia, HI4PI, SDSS,
and HST probes were not persisted into these indices.

Assets is deployed separately as Helm release `astro-survey-atlas-assets` in
namespace `astro-survey-atlas-assets`. It points at the Warehouse Service with
`ASSETS_WAREHOUSE_ES_URL` and reads only the three `ast_*` indices. Its current
runtime behavior replaces the static public footprint set whenever ACTIVE
Warehouse layers exist. This is an Assets integration bug: the public bundle
still contains 44 footprints across 14 surveys, but the deployed coverage
endpoint showed only 4 Warehouse footprints across `csst`, `desi`, and
`euclid`. Warehouse data is present; the next Assets session must merge static
public records with Warehouse overrides instead of treating Warehouse as the
complete catalog.

Exact probe inputs, hashes, counts, and reproduction details are in
`docs/contract-probe-results-20260825.md`. Live counts and cluster objects are
time-sensitive observations, not acceptance criteria.

## Known Contract Deviations

1. **Partial scans can become ACTIVE.** `ScanService.java` fails only when
   errors exist and all coverage is empty. The contract requires any item or
   write error to mark the refresh FAILED; a successful empty scan alone may be
   ACTIVE.
2. **FITS WCS is labeled ICRS without proving it.** `FitsHeaderHandler.java`
   validates TAN shape but not celestial axes/reference frame before emitting
   ICRS coverage.
3. **Missing catalog spatial columns can look like a valid empty scan.**
   `CatalogHandler.java` can return zero coverage and zero errors when the
   configured RA/Dec or HEALPix columns are absent.
4. **Failure evidence is incomplete.** Evidence is written after enumeration,
   coverage deletion, extraction, and Elasticsearch writes. Failures before
   that point can leave no inventory, normalized scan, or error evidence.
5. **Lease ownership is weak.** The lease is fixed to one hour with no renewal,
   and `ElasticsearchAdapter.java` lets the same `scanRunId` bypass an existing
   UPDATING lease. A long or duplicated Job can overlap writes.
6. **The scanner is not actually bounded in memory.** It retains every
   FileAsset and SpatialCoverage and serializes one full normalized JSON even
   though Elasticsearch writes are batched. This blocks safe large scans.
7. **Operator success can omit scanner truth.** `ScanRequestOperator.java`
   swallows summary/log retrieval failures, so a completed Job can be reported
   SUCCEEDED without a valid scanner summary.
8. **OSS FITS reads are full-object GETs.** `S3SourceAdapter.java` does not use
   byte Range requests, making header-only extraction unnecessarily expensive.
9. **Operator Jobs lose caller tracking labels.** Generated Jobs preserve
   internal identity labels but not the allowed labels needed for operational
   correlation.
10. **ADR-0004 is stale.** It still describes retained indexed scan history and
    must be marked superseded by the current-state decision to avoid reviving
    the old model.

## Next Session

Use this order; each step should add regression coverage before deployment:

1. Make every partial/error refresh FAILED while preserving a genuinely empty,
   error-free scan as ACTIVE. Verify ACTIVE-only queries hide failed partial
   edges.
2. Make evidence failure-safe from enumeration onward. A failed run must leave
   a source snapshot when available, phase, errors, counts, and provenance.
3. Validate FITS celestial axes/frame as ICRS and fail missing catalog columns
   explicitly. Re-run HST, Gaia, HI4PI, and Euclid contract probes.
4. Add unique lease ownership and renewal/heartbeat behavior; test expiration,
   duplicate run IDs, and long scans.
5. Stream enumeration, extraction, Elasticsearch writes, and evidence so peak
   memory is bounded independently of source size.
6. Add S3/OSS byte Range reads for FITS headers, with a controlled fallback for
   servers that do not honor ranges.
7. Require a parseable scanner summary before Operator success and propagate
   allowed tracking labels to Jobs.
8. Mark ADR-0004 superseded, rerun all 53+ tests and manifest validation, then
   perform bounded CSST/DESI/Euclid scans and the Assets direct-read smoke.
9. After Warehouse correctness is green, fix Assets startup-only refresh and
   its 10,000-coverage-document load limit.

## Do Not Disturb

- Keep `/home/aaron/Repo/data-warehouse` frozen and keep `astro_*` out of the
  new runtime path.
- Preserve all dirty files above unless their behavior is deliberately replaced
  with tests and updated contracts.
- Keep secrets in Kubernetes Secret or environment references. Never place
  access keys in plans, evidence, logs, fixtures, or commits.
- Do not scan the 19 TiB Euclid root as a content test. Use listing-only
  inventory, a bounded tile prefix, or exact object keys.
- Treat FITS multi-HDU WCS and FITS binary-table catalog support as separate
  feature decisions after the current contract deviations are closed.
