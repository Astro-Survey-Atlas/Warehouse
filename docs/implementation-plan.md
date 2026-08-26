# Implementation Plan

This file is the canonical progress ledger. Update it when a phase passes its
gate; `HANDOFF.md` records only the latest operational continuation point.

## Baseline

- [x] Local scanner, S3/OSS adapters, FITS/catalog extraction, ES adapter, and
  diagnostic query vertical slice.
- [x] Thin Operator, ScanRequest CRD, immutable ConfigMap/Job translation,
  Secret projection, container packaging, and k3s smoke test.
- [x] Baseline checkpoint `effa3c3`; 42 tests passing before redesign.

## Phase 1: Contracts

- [x] Define CoverageLayer, FileAsset, SpatialCoverage, ExtractionMode,
  SourceSnapshot, Entrypoint, and reserved SourceUnit.
- [x] Supersede fixed-order/history ADRs and record internal pipeline ownership.
- [x] Publish ScanPlan v2, multi-order index, query, and Operator target contracts.

Gate: every document describes current layer state, actual order/precision, and
`ast_*` isolation without public Handler ordering.

## Phase 2: Core And Scanner

- [x] Implement LayerSpec, ExtractionSpec, controlled modality/precision, layer
  state, multi-order SpatialCoverage, and Plan v2 validation.
- [x] Replace HandlerFactory with CoverageExtractor resolution for four modes.
- [x] Compute source inventory hash and emit normalized scan/error evidence.
- [x] Run layer refresh through lease, deletion, batches, verification, and
  ACTIVE/FAILED completion.

Gate: local FITS and catalog fixtures prove mode semantics, current replacement,
explicit order, errors, and no scientific-array reads.

## Phase 3: Persistence And Reads

- [x] Add strict layer/file/coverage mappings and layer-scoped adapter methods.
- [x] Implement active-layer, exact-order HEALPix lookup and FileAsset join.
- [x] Preserve point/cone diagnostics without using them as storage semantics.

Gate: fake-ES tests cover lock conflict/takeover, layer delete, failure hiding,
multi-order reads, stable cursor, truncation, and shared FileAsset IDs.

## Phase 4: Operator And Deployment

- [x] Migrate CRD examples and parser to Plan v2.
- [x] Add same-layer Job waiting and expose snapshot/evidence summary fields.
- [x] Mount an explicit evidence PVC in scanner Jobs and reject paths outside
  its mount root.
- [x] Rebuild only pre-release `ast_*` mappings and rerun the live k3s smoke
  against the v2 Operator/Scanner path.
- [x] Add a self-managed `atlas-warehouse` Helm release for Elasticsearch,
  MinIO, Kafka, and strict `ast_*` mapping bootstrap; keep legacy `warehouse`
  outside the runtime path.

Gate: Operator tests and live smoke update only the three `ast_*` indices; all
legacy `astro_*` resources remain unchanged, and persisted evidence has an
explicit PVC/object-store mount.

## Phase 5: Contract Probes And Assets Cutover

- [x] Probe HST image WCS, Gaia catalog, and HI4PI cube headers/catalog
  metadata, recording unsupported cases as evidence. HST primary-HDU WCS is
  explicitly unsupported today; Gaia and HI4PI completed with exact/estimated
  order-8 output as documented in `docs/contract-probe-results-20260825.md`.
- [x] Expand the real OSS probe beyond one Euclid FITS file: inventory the
  `MER/` root metadata, enumerate the bounded `MER/102018212/` tile, and scan
  bounded VIS, NISP, and DECAM products, including catalog-FITS unsupported
  cases without adding a new extractor.
- [x] Probe a local SDSS spectral FITS header and a DESI catalog with the v2
  scanner in memory; both produced explicit order-8 coverage without reading
  scientific arrays.
- [x] Verify Assets direct lookup of ACTIVE layers, files, modality, order,
  precision, entrypoint fallback, and truncation against the configured
  deployment endpoint.
- [x] Update handoff and cutover runbook; do not migrate historical results.

Gate: `mvn test`, `mvn package`, template verification, and targeted smoke tests
all pass from a clean checkout plus the self-managed `atlas-warehouse` services.
