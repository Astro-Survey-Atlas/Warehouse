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

# Operator Contract

The namespaced `atlas.zhejianglab.org/v1alpha1` CRDs are Kubernetes submission
surfaces, not replacements for the domain contracts. `ScanRequest` carries a
canonical ScanPlan v2, execution settings, and Secret key references.
`MocDiscoveryRequest` carries a bounded public-source intent and policy.

## Multi-Namespace Deployment

One Operator Deployment runs in its Helm release namespace and watches a configured,
explicit, non-empty allowlist such as:

```text
WATCH_NAMESPACES=atlas-warehouse
```

The Operator opens one namespaced watch/list scope per entry. An empty value is
invalid and must fail closed; it must never become Fabric8 `inAnyNamespace()`.
The same namespace allowlist applies to ScanRequest and MocDiscoveryRequest,
but they run in separate controller Deployments and ServiceAccounts.

The Operator ServiceAccount is namespaced with the Helm release. The supported
Helm chart renders the CRDs, one Role and one RoleBinding in each watched namespace. Those
Roles grant only the custom resources and status, Jobs, ConfigMaps, Pods, and
Pod logs needed for reconciliation. They do not grant Secret reads, arbitrary
namespace access, or cluster-wide workload access.

## Namespace-Local Ownership

For every request, the Operator creates or observes resources in the request's
namespace:

| Resource | Ownership rule |
| --- | --- |
| Plan ConfigMap | Immutable, secret-free, owned by the request |
| Scanner/MOC Job | Owned by the request; execution identity includes plan hash |
| Pod and logs | Read only in the request namespace |
| Evidence PVC | Must already exist in the request namespace |
| Source PVC | Optional for local plans; must be `Bound`, labelled `atlas.zhejianglab.org/scanner-source=true`, and mounted read-only |
| Source/sink Secret | Referenced by name/key only; values are never read by reconcile |

Kubernetes owner references are namespace-local by design. A request with the
same name in two watched namespaces produces two independent executions.

## Evidence Storage

Persisted ScanPlan execution requires `spec.scanner.evidence.claimName`. The
Operator mounts that PVC at `/var/lib/atlas-evidence` by default (or the
configured absolute `mountPath`) and rejects an `evidence.outputPath` outside
the mount. The scanner writes source inventory, normalized scan, provenance,
and extraction/write errors there. A CSI-backed object-store volume satisfies
the same contract; direct object-store writes are deferred.

Local plans use a separate `scanner.sourceVolume` binding. Its `claimName` is
resolved in the ScanRequest namespace and must reference an existing `Bound`
PVC carrying `atlas.zhejianglab.org/scanner-source=true`. The Job mounts it
read-only at the declared absolute `mountPath` (normally `/data`) and applies
an optional relative `subPath`. The local plan `source.location.rootPath` must
remain below that mount. Assets validates the same contract before submission;
the Operator repeats it before Job creation. Node-specific host paths and
cross-namespace PVC references are not supported.

MOC discovery uses an independent evidence path below the configured mount. Its
Job never writes Elasticsearch or publishes a CoverageLayer.

## Scan Reconciliation

For a valid ScanRequest the Operator:

1. Validates ScanPlan through `spatial-core` before source access.
2. Renders a secret-free immutable plan ConfigMap.
3. Projects Secret keys through environment/file references without reading values.
4. Creates or adopts the plan/execution-hash-named scanner Job.
5. Waits when another non-terminal Job refreshes the same layer.
6. Reports Job phase and a parseable scanner summary.

Changing a plan, credential binding, image, or execution setting creates a new
execution after the current layer Job terminates. Equivalent active work is
adopted; a successful equivalent Job wins over a stale failed duplicate. Job
TTL is operational cleanup, not indexed scan history.

### Scanner failure and retry policy

Scanner and MOC discovery Jobs are created with `backoffLimit=0`. The Operator
rejects a non-zero
`spec.scanner.backoffLimit`, so a deterministic extraction error cannot trigger
another full source enumeration. The scanner stops at the first extraction
error, records it in Evidence, marks the layer `FAILED`, and does not skip to
later files; a malformed Catalog row stops that file and the scan as well.
Callers retry by submitting a new ScanRequest after reviewing the retained
Evidence. Normal suffix, blank-line, and comment filtering is not an error.
S3 and Elasticsearch transport adapters retain only their bounded request/item
retries and explicit timeouts.

MOC discovery has the same no-replay Job policy. Its allowlisted HTTP request
and task limits remain bounded; a caller submits a new discovery request when a
failed evidence run should be repeated.

## MOC Discovery Reconciliation

For a valid `MocDiscoveryRequest`, the discovery Operator accepts only
`policyRef: cds-public-moc-v2`, creates one bounded evidence-only Job, and
reports `phase`, `jobName`, `evidencePath`, and `candidateCount` from the Job's
compact completion marker. The marker contains no response body; complete
evidence stays on the evidence mount. Discovery does not probe or publish MOCs;
Assets owns the subsequent build request.
An HTTP 200 response with an empty body is a protocol error and is retained as
failure evidence. A valid empty JSON result is a successful bounded observation,
not evidence that the survey does not exist. See [`moc-discovery.md`](moc-discovery.md).

## Status

ScanRequest phases are `INVALID`, `WAITING`, `SUBMITTED`, `RUNNING`,
`SUCCEEDED`, and `FAILED`. Scanner summaries include layer ID, run ID, source
snapshot hash, counts, available orders, errors, and evidence path. Missing
evidence storage or credential references fail before source access.

## Deliberate Limits

The Operator contains no source enumeration, WCS, HEALPix, evidence generation,
or Elasticsearch code. It creates finite Jobs and provides no schedules, DAGs,
arbitrary commands, user plugins, or cross-namespace owner references.
