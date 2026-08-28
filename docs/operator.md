# Operator Contract

The namespaced `atlas.zhejianglab.org/v1alpha1` ScanRequest carries a canonical
ScanPlan v2, scanner execution settings, and Secret key references. The alpha
CRD remains the same while the embedded plan moves from version 1 to version 2.

## Evidence Storage

Evidence is not an Elasticsearch document and is not part of the public
release request. A persisted plan must set `spec.scanner.evidence.claimName`.
The Operator mounts that PVC into the scanner Job at
`/var/lib/atlas-evidence` by default (or the configured absolute `mountPath`),
and requires `plan.evidence.outputPath` to be below that directory. The scanner
writes the source inventory, normalized scan, and extraction errors there.
The PVC must exist in the ScanRequest namespace. A CSI-backed object-store
volume can satisfy this contract; direct object-store evidence writes are
deferred.

## Deployment Boundary

The supported cluster deployment uses `atlas-warehouse` for Scanner Jobs,
evidence PVCs, and namespace-local credential references. The repository Helm
release `deploy/helm/atlas-warehouse-infra` owns the new Elasticsearch, MinIO,
and strict `ast_*` mapping bootstrap. Kafka is optional
(`kafka.enabled=false` by default) and is not used by the current Scanner or
Operator. Scanner sink plans use
`atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200`; the
legacy `warehouse` Services and `astro_*` indices are never runtime fallbacks.

## Reconciliation

For a valid request the Operator:

1. Validates ScanPlan through `spatial-core` before creating resources.
2. Renders a secret-free immutable plan ConfigMap.
3. Projects credentials through Secret environment/file references without
   reading their values.
4. Labels the Job and Pod with a DNS-safe layer identity.
5. Reuses an equivalent Job already owned by the ScanRequest when an Operator
   rollout changed the historical execution hash; active work wins, then a
   successful equivalent Job wins over a stale failed duplicate.
6. Waits when another non-terminal Job for the same layer exists, then creates
   the plan/execution-hash-named scanner Job and reports its summary.

Changing plan, credential bindings, image, or execution settings creates a new
Job after the current layer Job terminates. Equivalent Jobs are matched by the
rendered plan hash and scanner image so an Operator upgrade cannot orphan an
in-flight or already successful execution. Completed Jobs remain under TTL for
diagnosis; they are not indexed result history. Elasticsearch layer leases also
protect CLI and cross-request concurrency.

## Status

CR phases are `INVALID`, `WAITING`, `SUBMITTED`, `RUNNING`, `SUCCEEDED`, and
`FAILED`. Scanner summary includes layer ID, run ID, snapshot hash, counts,
available orders, errors, and evidence path. Missing evidence storage or
credentials fail before source access.

## Deliberate Limits

The Operator contains no source enumeration, WCS, HEALPix, evidence generation,
or Elasticsearch code. It creates one finite Job and does not provide schedules,
DAGs, arbitrary commands, or user plugins.
