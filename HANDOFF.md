# Warehouse Session Handoff

Updated: 2026-08-26

Repository: `/home/aaron/Repo/Astro-Survey-Atlas-Warehouse`

Starting commit: `3f7df52`

## Start Here

Read `AGENTS.md`, `CONTEXT.md`, `docs/requirements.md`,
`docs/project-boundary.md`, `docs/architecture.md`, and the contract relevant
to the code being changed. The Assets integration handoff is
`/home/aaron/Repo/Astro-Survey-Atlas-Assets/HANDOFF.md`.

This repository has intentional uncommitted deployment, documentation, probe,
and local configuration work. The implementation fixes are in the current
Warehouse history (`47c8618`); preserve every remaining change shown by
`git status --short`, including the bounded ScanRequest manifests and probe
results. These files are not disposable generated output. The sibling
`/home/aaron/Repo/data-warehouse` is a frozen legacy/reference repository and
must remain untouched.

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
includes inventory, errors, and a normalized scan document. The current refresh
path fails partial/error scans, writes failure evidence, uses expiring
execution-owned leases with heartbeat/takeover, streams enumeration and writes,
uses bounded object-store reads for FITS headers, and requires a parseable
scanner summary before Operator success. Allowed tracking labels are copied to
generated Jobs.

FITS support reads headers rather than scientific arrays. Linear TAN image WCS
is sampled into estimated cells only after the celestial axes and explicit ICRS
frame validate; explicit header coordinates produce an entrypoint-only cell.
CSV/TSV validates configured RA/Dec or NESTED HEALPix columns before reading
rows. HST WCS in a later `SCI` HDU and FITS binary-table catalogs remain
explicitly unsupported and fail visibly rather than inventing coverage.

## Live Deployment Layout

The current cluster runs the self-managed Warehouse infrastructure as Helm
release `atlas-warehouse`, revision 1, in namespace `atlas-warehouse`, from
the repository chart `atlas-warehouse-infra-0.1.0`. It owns the single-node
Elasticsearch, Kafka, MinIO, and strict `ast_*` mapping bootstrap needed by
this product. It deliberately does not install Flink or the legacy
metadata-ingest operator: the v1 scanner writes bounded batches directly and
Flink is outside the current product boundary. The stable services are:

```text
http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200
http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000
atlas-warehouse-kafka.atlas-warehouse.svc.cluster.local:9092
```

The old `warehouse` release and namespace are absent. The five old
`Released/Retain` PV objects and their NFS directories for the former
Elasticsearch, MinIO, Flink, and evidence claims were explicitly released on
2026-08-26. The shared `nfs-data` provisioner and unrelated retained PVs remain
in place for other namespaces.

The Warehouse scanner/operator code is deployed separately. The Operator runs
as Deployment `astro-atlas-operator` in namespace `atlas-system`, using the
image
`crpi-wixjy6gci86ms14e.cn-hongkong.personal.cr.aliyuncs.com/ay-dev/astro-atlas-operator:0.2.0-20260826-operatorfix3`.
It watches `ScanRequest` resources in `atlas-warehouse`, creates immutable plan
ConfigMaps and one scanner Job per rendered plan, and injects the image
`crpi-wixjy6gci86ms14e.cn-hongkong.personal.cr.aliyuncs.com/ay-dev/astro-atlas-scanner:0.2.0-20260826-shutdownfix1` for requests that do not pin an
image. The in-flight CSST full-prefix retry deliberately uses the previous
`bulkfix1` image and is not modified in place; its checked-in retry manifest
now points to `shutdownfix1` for future runs.
Jobs read OSS/S3/MinIO through Secret references, write the three `ast_*`
indices, and persist inventory/normalized/error evidence to the
`atlas-warehouse`-local `atlas-evidence-smoke` PVC.

The checked-in installation sequence is: build Maven jars, build and push the
scanner/operator images, apply `namespace.yaml`, `crd.yaml`, `rbac.yaml`, and
`operator-deployment.yaml`, create an evidence PVC and credential Secrets, then
apply a `ScanRequest` manifest. See `deploy/kubernetes/README.md`; the
CSST/DESI examples under `deploy/kubernetes/scanrequest-*.yaml` are bounded
workloads, not full-survey scans. The scanner bulk writer uses 100-record
batches and a 90-second request timeout for large catalog prefixes.

## Verification Baseline

The latest local gate was green:

```text
mvn test                         # 66 tests passed
mvn package -DskipTests          # passed
Helm template/lint and kubectl dry-run  # checked chart/manifests
git diff --check                 # clean for the reviewed files
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
- SDSS spectral FITS produced one entrypoint-only order-8 cell. A historical
  local HI4PI 3D-cube probe produced 7,494 estimated spatial cells while
  ignoring the spectral axis; the current strict ICRS validation instead keeps
  the non-explicit-ICRS header as failed evidence.
- HST multi-HDU WCS and two Euclid FITS binary-table catalogs produced explicit
  unsupported/error results with no fabricated coverage.
- Assets read ACTIVE CSST, DESI, and Euclid layers from the `ast_*` indices and
  completed catalog, overlap, and file reverse-lookup requests.

The completed bounded-smoke baseline before the current full-prefix retry was
13 layer documents, 11 FileAssets, and 2,109 coverage edges. The current retry
adds edges while its layer remains `UPDATING`; its final counts are recorded
after the Job reaches a terminal state. The final bounded smoke layers are:

| ScanRequest | Result |
| --- | --- |
| `final-csst-catalog-retry-20260826` | `SUCCEEDED`, 1 file, 5 edges, 0 errors |
| `final-csst-image-20260826` | `FAILED`, missing FITS spatial header |
| `final-desi-catalog-20260826` | `SUCCEEDED`, 1 file, 2,039 edges, 0 errors |
| `final-desi-overlap-20260826` | `SUCCEEDED`, 1 file, 5 edges, 0 errors |
| `final-euclid-vis-20260826` | `SUCCEEDED`, 1 file, 11 edges, 0 errors |

The first CSST catalog attempt and the CSST image failure remain as
ScanRequest/Job/evidence records; the retry is the authoritative current layer
state. The current CSST image layer is `FAILED` and therefore contributes no
queryable coverage. Controlled Assets modality probes also leave Gaia and SDSS
ACTIVE layers and the non-ICRS HI4PI cube as explicit `FAILED` evidence. The
Euclid `MER/` root remains inventory-only: 15,948 FITS objects, about 19 TiB,
were listed but not bulk-scanned. The bounded Euclid VIS tile retry2 excludes
two PSF FITS files with missing spatial headers and keeps their failure
evidence; it is the current successful refresh for that layer. The CSST W1
catalog full-prefix retry is still running under its six-hour deadline; its
final summary and layer state are recorded after completion. HST multi-HDU and FITS binary-table
probes remain explicit unsupported/error evidence.

Assets is deployed separately as Helm release `astro-survey-atlas-assets`
(revision 76, image `0.1.0-20260826-184451`) in namespace
`astro-survey-atlas-assets`. It points at the new
Warehouse Service with `ASSETS_WAREHOUSE_ES_URL` and reads only the three
`ast_*` indices. Its runtime now merges the 44-record static public bundle
with ACTIVE Warehouse layers, paginates coverage reads, and exposes controlled
reload/status endpoints. While the CSST retry is `UPDATING`, the live public
coverage response contains 53 footprints; bounded smoke passed for health,
catalog/block reads, CSST/DESI and DESI/Euclid overlap, overlap details,
reverse lookup, and FITS Range reads. After the CSST layer becomes `ACTIVE`,
call the protected catalog reload endpoint and repeat the coverage smoke.

Exact probe inputs, hashes, counts, and reproduction details are in
`docs/contract-probe-results-20260825.md`. Live counts and cluster objects are
time-sensitive observations, not acceptance criteria.

## Closed Contract And Deployment Issues

The previous handoff's correctness gaps are closed and regression-tested:

- Partial, item, and write errors leave a layer `FAILED`; only a genuinely
  empty, error-free refresh may become `ACTIVE`, and failed edges stay hidden.
- Failure-safe evidence records phase, counts, source snapshot information
  when available, provenance, and extraction/write errors.
- FITS WCS requires explicit ICRS-compatible celestial metadata; missing or
  non-ICRS metadata fails visibly. Missing configured catalog columns fail
  before a catalog can look like a valid empty scan.
- Lease ownership is execution-specific and supports heartbeat renewal,
  expiration, takeover, and duplicate-run conflict handling.
- Enumeration, extraction, evidence, and Elasticsearch writes use bounded
  streaming/batches. OSS/FITS header reads use byte ranges with a controlled
  fallback.
- Operator success requires a parseable scanner summary and propagates allowed
  tracking labels to generated Jobs. Operatorfix3 serializes reconcile callbacks
  and reuses equivalent Jobs by request label, plan hash, and scanner image;
  non-terminal work is adopted and successful duplicates win over stale failed
  duplicates.
- ADR-0004 is already marked superseded by ADR-0009; the current-state model is
  also recorded in the index and requirements contracts.

The remaining v1 limitations are deliberate: HST WCS in a later `SCI` HDU and
FITS binary-table catalogs fail explicitly until separate contracts are added.

## Next Session

Use this order for future changes:

1. Keep the self-managed `atlas-warehouse` release, Operator, evidence PVC, and
   Assets endpoint healthy; rerun bounded scans after image or mapping changes.
2. Preserve failed ScanRequests/evidence as operational evidence and verify
   `FAILED` layers remain absent from ACTIVE-only reads.
3. Treat HST multi-HDU WCS and FITS binary-table catalog support as separate
   contract decisions. Do not expand the Euclid root scan beyond inventory-only
   or bounded exact-key tests.
4. Keep unrelated retained PVs and the shared `nfs-data` provisioner outside
   Warehouse application rollouts; the old `warehouse` storage has already
   been released.

## Do Not Disturb

- Keep `/home/aaron/Repo/data-warehouse` frozen and keep `astro_*` out of the
  new runtime path.
- Preserve all dirty files in both repositories, all failed ScanRequests and
  evidence, and all deployment/probe manifests unless their behavior is
  deliberately replaced with tests and updated contracts.
- Keep secrets in Kubernetes Secret or environment references. Never place
  access keys in plans, evidence, logs, fixtures, or commits.
- Do not scan the 19 TiB Euclid root as a content test. Use listing-only
  inventory, a bounded tile prefix, or exact object keys.
- Do not touch unrelated retained PVs or the shared storage provisioner as part
  of a Warehouse application rollout.
- Treat FITS multi-HDU WCS and FITS binary-table catalog support as separate
  feature decisions after the current contract deviations are closed.
