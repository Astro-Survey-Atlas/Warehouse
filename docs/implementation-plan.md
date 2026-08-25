# Implementation Plan

## Delivery Strategy

Build one narrow vertical slice first, then add Kubernetes orchestration. The Operator is retained in the product, but the scientific contract must be executable and testable without a cluster before the Operator can add value.

## Phase 0: Contracts And Build Skeleton

Deliver:

- Root Maven build using Java 17.
- `spatial-core`, `scanner-cli`, `query-api` module skeletons.
- Context, requirements, boundary, architecture, ScanPlan, index, and query contracts kept current.
- Test conventions and local fixture layout.

Completion criterion: `mvn test` runs from the root and all module dependencies point through `spatial-core`.

## Phase 1: `spatial-core`

Deliver:

- Domain value types for FileAsset, SpatialCoverage, InputItem, Modality, ScanPlan, MetadataRecord, and SpatialQuery.
- Plan and query validation.
- Stable source URI canonicalization and FileAsset ID derivation.
- HEALPix conversion using ICRS and NESTED order 8.
- FITS header/WCS spatial extraction.
- CSV/TSV coordinate and HEALPix extraction contracts.
- FileAsset and SpatialCoverage document models.
- Coverage candidate normalization and de-duplication.

Completion criterion: deterministic unit tests cover valid and invalid coordinate, WCS, catalog, ID, plan, and index-document cases without a network or Kubernetes dependency.

## Phase 2: `scanner-cli`

Deliver:

- Plan JSON loading and validation.
- Local filesystem enumeration first.
- S3 and OSS source adapters behind the same source seam.
- Ordered in-process Handler pipeline.
- FITS, CSV/TSV, default asset, coverage normalization, and header-only spectral extension points.
- Elasticsearch bulk writer with bounded batches, retry policy, stable upserts, and redacted summaries.
- Local fixture command that produces the new indices.

Completion criterion: a local FITS fixture and a local CSV/TSV fixture produce FileAsset and deduplicated SpatialCoverage documents through the same scanner path; a spectral fixture is indexed without reading its scientific array.

## Phase 3: `query-api`

Deliver:

- Lightweight Java HTTP process.
- Health/readiness endpoints.
- Point, cone, and HEALPix endpoints.
- Stable cursor pagination and query de-duplication.
- Elasticsearch read adapter and safe error responses.

Completion criterion: an integration test indexes fixture documents, calls every query type, verifies de-duplicated results and cursor continuation, and proves the API has no write path.

## Phase 4: Operator Adapter

Deliver:

- Astronomy-specific scan request CRD, with the exact kind and API version chosen from the stable ScanPlan contract.
- Reconciler that validates references and creates a scanner Job.
- Secret reference propagation without copying secret values into CR status or logs.
- Job status mapping and scanner summary propagation.
- Explicit retry, concurrency, and cleanup policy for the Job resource.
- Helm packaging and RBAC for the Operator and scanner Job.

Completion criterion: a Kubernetes integration test or an environment-gated test submits one scan request, observes a scanner Job, and verifies terminal status while all spatial behavior remains covered by `spatial-core` tests.

## Phase 5: Deployment Hardening

Deliver:

- Container packaging for scanner and query API.
- Elasticsearch index templates and deployment checks.
- S3/OSS credential reference examples without secret values.
- Resource, timeout, and bulk settings.
- Operational runbook and failure diagnosis.

Completion criterion: a clean environment can deploy the query API and Operator, run a fixture or configured scan, and query the new indices without touching the legacy repository or legacy indices.

## Reuse From Legacy

Copy and simplify proven algorithms only after their external behavior is covered in the new module:

- HEALPix conversion and validation.
- FITS header/WCS extraction.
- CSV/TSV spatial-column handling.
- S3/OSS/local enumeration.
- Elasticsearch bulk batching, retry, and stable upsert behavior.

Do not copy the legacy CRD hierarchy, Flink/Cron execution chain, Kafka residence workflow, or broad task status model into the new core.

## Verification Commands

The initial repository must make these commands truthful before they are documented as required:

```text
mvn test
mvn package
```

Additional integration commands should be added only when the corresponding environment and test profile exist.
