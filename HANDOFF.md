<!--
Copyright 2026 Astro Survey Atlas contributors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Warehouse Session Handoff

Updated: 2026-08-30

Repository: `/home/aaron/Repo/Astro-Survey-Atlas-Warehouse`

Starting commit: `3f7df52`

## Start Here

Read `AGENTS.md`, `CONTEXT.md`, `docs/requirements.md`,
`docs/project-boundary.md`, `docs/architecture.md`, and the contract relevant
to the code being changed. The Assets integration handoff is
`/home/aaron/Repo/Astro-Survey-Atlas-Assets/HANDOFF.md`.

This repository has intentional uncommitted deployment, documentation, probe,
and local configuration work. The implementation fixes are in the current
Warehouse history (`d9526c6`); preserve every remaining change shown by
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

The scanner's Java HEALPix extraction is covered by the shared
MOC-Core-SDK conformance fixture (`MOC-Core-SDK@2ebc395`). Warehouse consumes
the fixture for numerical parity; it does not vendor or expose a separate
scientific Core implementation.

## Live Deployment Layout

The current cluster runs the self-managed Warehouse infrastructure as Helm
release `atlas-warehouse`, revision 1, in namespace `atlas-warehouse`, from
the repository chart `atlas-warehouse-infra-0.1.0` (the repository chart is now
version `0.1.1`; the live release has not been upgraded). The chart owns the
single-node Elasticsearch, MinIO, and strict `ast_*` mapping bootstrap needed
by this product. Kafka is now an optional dependency, disabled by default; the
current Scanner/Operator path writes bounded batches directly and has no Kafka
producer or consumer. The default profile does not install Flink or the legacy
metadata-ingest operator, but a future event-driven/Flink profile may enable
Kafka. The stable services are:

```text
http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200
http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000
```

An existing release may still have the previously enabled Kafka StatefulSet
until an explicit Helm upgrade applies `kafka.enabled=false`. Do not perform
that infrastructure upgrade while a long-running scan is being observed unless
the operational impact has been reviewed; changing chart defaults in the
repository does not stop existing Jobs.

The old `warehouse` release and namespace are absent. The five old
`Released/Retain` PV objects and their NFS directories for the former
Elasticsearch, MinIO, Flink, and evidence claims were explicitly released on
2026-08-26. The shared `nfs-data` provisioner and unrelated retained PVs remain
in place for other namespaces.

The Warehouse scanner/operator code is deployed separately. The ScanRequest
Operator runs as Deployment `astro-atlas-operator` in namespace `atlas-system`,
using image
`crpi-wixjy6gci86ms14e.cn-hongkong.personal.cr.aliyuncs.com/ay-dev/astro-atlas-operator:0.2.0-20260830-143932`.
It watches `ScanRequest` resources in `atlas-warehouse`, creates immutable plan
ConfigMaps and one scanner Job per rendered plan, and injects the image
`crpi-wixjy6gci86ms14e.cn-hongkong.personal.cr.aliyuncs.com/ay-dev/astro-atlas-scanner:0.2.0-20260829-pvc1` for requests that do not pin an
image. The previously running CSST full-prefix retry used the older `bulkfix1`
image and was not modified in place; it has since failed on its six-hour
deadline. New retries must use `shutdownfix1` (or a deliberately pinned image)
and a bounded prefix/deadline appropriate to the workload.
MOC discovery runs in a separate `astro-atlas-moc-discovery` Deployment and
ServiceAccount, using the operator image above only for the dedicated
`MocDiscoveryMain` entrypoint. Its Job image is
`crpi-wixjy6gci86ms14e.cn-hongkong.personal.cr.aliyuncs.com/ay-dev/astro-atlas-moc-discovery:0.1.0-20260830-143932`.
Jobs read OSS/S3/MinIO through Secret references, write the three `ast_*`
indices, and persist inventory/normalized/error evidence to the
`atlas-warehouse`-local `atlas-evidence-smoke` PVC.

On 2026-08-29 Infra revision 2 added the Warehouse-owned static source PVC
`atlas-source-catalogs` (1800Gi, ReadOnlyMany, NFS export
`10.15.49.212:/mnt/data/catalogs`) labelled
`atlas.zhejianglab.org/scanner-source=true`. Local ScanRequests mount this PVC
read-only through `scanner.sourceVolume`; Assets no longer creates source
PV/PVC resources or passes node-specific host paths. The deployed Operator is
image `0.2.0-20260830-143932` and scanner image `0.2.0-20260829-pvc1`; its
dedicated MOC discovery controller uses the same operator image and creates
Jobs with discovery image `0.1.0-20260830-143932`.

The bounded COSMOS CSV ScanRequest
`cosmos-parameter-prediction-catalog-20260829` completed successfully with
one file, 298,232 valid rows, 19 order-8 coverage records and zero extraction
errors. Evidence was written under
`/var/lib/atlas-evidence/cosmos-parameter-prediction-20260829`.

The checked-in installation sequence is: build Maven jars, build and push the
scanner/operator images, apply `namespace.yaml`, `crd.yaml`, `rbac.yaml`, and
`operator-deployment.yaml`, create an evidence PVC and credential Secrets, then
apply a `ScanRequest` manifest. See `deploy/kubernetes/README.md`; the
CSST/DESI examples under `deploy/kubernetes/scanrequest-*.yaml` are bounded
workloads, not full-survey scans. The scanner bulk writer uses 100-record
batches and a 90-second request timeout for large catalog prefixes.

## Verification Baseline

The latest local gate on 2026-08-28 was green:

```text
mvn -B -q test                   # passed
mvn -B -q verify                 # passed
Helm template/lint and kubectl dry-run  # checked chart/manifests
git diff --check                 # clean for the reviewed files
```

The 2026-08-30 MOC v2 review-status fix passed `mvn -B -q test` and was
deployed with the updated CRD, isolated operator/discovery images, and
dedicated controller. The live CRD now declares the
structured `status.reviewSummary` fields without requiring empty
`candidates`/`probes` arrays, which Fabric8 may omit during serialization. The
discovery worker now uses the CDS MOCServer filter API rather than the
unsupported ADQL request and records empty/malformed responses as protocol
evidence. The verified JWST retry
`jwst-moc-discovery-fix-20260830114238` is `SUCCEEDED` with 16 candidates, 10
probes, and 10 accepted spatial MOCs (`maxOrder=12`, ICRS/NESTED). The earlier
ADQL attempts remain historical zero-result/legacy records.

The live `mocdiscoveryrequests` CRD accepts only `cds-public-moc-v2`; applying a
legacy v1 object is intentionally unsupported. Existing v1 objects remain
read-only historical records. A v2 retry
`jwst-moc-discovery-fix-20260830114238-retry-20260830064604` completed with 16
candidates, `schemaVersion=2`, `searchRecordCount=16`, and `truncated=false`.

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

The completed bounded-smoke baseline remains historical evidence. One
2026-08-28 observation saw 15 layer documents, 22,844 FileAssets, and 92,487
physical coverage documents; the retry is actively replacing coverage, so
these counts are time-sensitive and physical coverage includes failed/partial
evidence. They are not an ACTIVE-only publication count. The known bounded
smoke layers are:

| ScanRequest | Result |
| --- | --- |
| `final-csst-catalog-retry-20260826` | `SUCCEEDED`, 1 file, 5 edges, 0 errors |
| `final-csst-image-20260826` | `FAILED`, missing FITS spatial header |
| `final-desi-catalog-20260826` | `SUCCEEDED`, 1 file, 2,039 edges, 0 errors |
| `final-desi-overlap-20260826` | `SUCCEEDED`, 1 file, 5 edges, 0 errors |
| `final-euclid-vis-20260826` | `SUCCEEDED`, 1 file, 11 edges, 0 errors |

The first CSST catalog attempts and the CSST image failure remain as
ScanRequest/Job/evidence records. The previous `csst-w1-phot-catalog` refresh
`oss-csst-w1-catalog-full-bulkfix2-20260826` reached `DeadlineExceeded` at
2026-08-27 21:03 UTC after its six-hour deadline, leaving an expired
`UPDATING` lease and no final snapshot hash. Recovery retry3 now owns the layer
lease and is the active operational task; it must end as `ACTIVE` or an
explicit `FAILED` result before Assets treats the layer as settled. The current
CSST image layer is `FAILED` and contributes no queryable coverage. Controlled
Assets modality probes leave Gaia and SDSS ACTIVE and the non-ICRS HI4PI cube
as explicit FAILED evidence. The Euclid `MER/` root remains inventory-only:
15,948 FITS objects (about 19 TiB) were listed but not bulk-scanned. HST
multi-HDU and FITS binary-table probes remain explicit unsupported/error
evidence.

Recovery retry `oss-csst-w1-catalog-full-retry3-20260828` was submitted on
2026-08-28 with the `shutdownfix1` scanner image and a 24-hour deadline. It
reached `FAILED / DeadlineExceeded` on 2026-08-29T12:15:39Z; the generated Job
was `oss-csst-w1-catalog-full-retry3-20260828-scan-6568995b57`. Preserve that
Job and its evidence. The `csst-w1-phot-catalog` layer remains non-public until
a new bounded retry finishes as `ACTIVE` or an explicit `FAILED` result is
recorded.

Assets is deployed separately as Helm release `astro-survey-atlas-assets`
(revision 84, image `0.1.0-20260828-215713`) in namespace
`astro-survey-atlas-assets`. It points at the new
Warehouse Service with `ASSETS_WAREHOUSE_ES_URL` and reads only the three
`ast_*` indices. Its runtime now merges the 47-record static public bundle
with ACTIVE Warehouse layers, paginates coverage reads, and exposes controlled
reload/status endpoints. The live public coverage response currently contains
56 footprints. Health and read-only coverage smoke are passing; the protected
catalog reload and post-recovery CSST smoke remain pending until the CSST layer
has a terminal state.

The Assets worktree now also contains four reviewed public CDS MOC layers:
`skymapper-dr4-color-footprint`, `kids-dr5-color-footprint`,
`vista-viking-j-footprint`, and `decals-dr5-color-footprint`. Their source
snapshots, record hashes, generated MOCs, order-4 previews, order-8 query
blocks, statistics, and provenance are locked under
`/home/aaron/Repo/Astro-Survey-Atlas-Assets`; the offline bundle reports 90
products, 38 acquired products, 47 manifest footprints, and 10 Core layers.
The live deployment remains at its previous 53-footprint response until an
explicit Assets release/reload is performed.

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

Use this order for operational follow-up; documentation and release changes in
this session do not alter the running CSST Job:

1. Decide whether `csst-w1-phot-catalog` still merits another bounded retry;
   if so, submit a new ScanRequest with a bounded prefix/deadline and the
   current scanner image. Preserve the failed retry3 Job and evidence.
2. After the layer is `ACTIVE` or explicitly `FAILED`, verify counts/evidence,
   reload Assets through the protected endpoint only on success, and rerun the
   catalog/overlap/reverse-lookup/Range smoke.
3. Retain the completed Gaia, SkyMapper, KiDS, VISTA VIKING, and DECaLS
   `MocDiscoveryRequest` evidence. All five CDS ObsCore searches returned HTTP
   200 with an empty body, so candidate/probe counts are zero; this is a bounded
   discovery result, not proof that the reviewed CDS MOCs are absent.
4. Preserve failed ScanRequests/evidence and verify `FAILED` layers remain
   absent from ACTIVE-only reads. Keep HST multi-HDU WCS and FITS binary-table
   catalog support as separate contract decisions; do not expand the Euclid
   root scan beyond inventory-only or bounded exact-key tests.
5. Keep unrelated retained PVs and the shared `nfs-data` provisioner outside
   Warehouse application rollouts; the old `warehouse` storage has already
   been released.

The repository release-readiness work is complete: MOC Jobs now carry request
owner references and emit compact terminal summaries, the release workflow
stages runner artifacts and pushes both charts to GHCR OCI, and ADRs 0012-0014
record namespace scope, chart separation, and optional event-driven
deployment. Run the final local gates before publishing a tag.

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
