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

# MOC Discovery

MOC Discovery is a bounded evidence workflow for finding public survey MOCs or
metadata endpoints. It is intentionally separate from a Warehouse scan and does
not publish a `CoverageLayer`.

## Request

`MocDiscoveryRequest` is a namespaced `atlas.zhejianglab.org/v1alpha1` resource:

```yaml
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: MocDiscoveryRequest
metadata:
  name: gaia-moc-discovery
  namespace: atlas-warehouse
spec:
  query:
    surveyName: Gaia
    releaseHint: DR3
    productHint: source
  policyRef: cds-public-moc-v2
```

Only the fixed `cds-public-moc-v2` policy is accepted. It controls
allowlisted hosts, request count, candidate limits, response bytes,
maximum task bytes, output order, and timeouts. No user-provided URL or
arbitrary command is accepted.

The policy uses the CDS `MocServer` filter API, not the ObsCore ADQL endpoint:

```text
GET https://alasky.cds.unistra.fr/MocServer/query
  ?expr=obs_collection=*<survey>* && (...hints...)
  &get=record&fmt=json&MAXREC=51&casesensitive=false
  &fields=ID,...,moc_access_url,hips_service_url,...
```

The worker retains the original record and the response as evidence and hashes
each body. It does not download candidate MOCs or run probes. Assets performs
that acquisition only after an explicit build request, using the candidate URL
from this summary and a locked SHA-256 snapshot.

## Execution And Evidence

The Operator creates one independent evidence-only Job in the request
namespace. The Job writes an execution plan, upstream response metadata,
candidate/probe records, hashes, errors, and truncation state below the evidence
mount. It never writes `ast_layer_index_v1`, `ast_file_index_v1`, or
`ast_coverage_index_v1`.

Status exposes `phase`, `jobName`, `evidencePath`, and `candidateCount` after a
completed Job emits its compact completion marker. The marker contains at most
50 candidate summaries; the search reads 51 records so the 51st can reliably
set `truncated` without entering status. Full evidence remains on the evidence
mount, so reconcile logs do not contain upstream response bodies. A successful
Job with zero candidates is a valid empty query, not proof that the survey has
no public MOC. An HTTP success with an empty or malformed body is instead a
transport/protocol error and remains visible in evidence. `truncated` and
transport errors are never collapsed into a zero-result claim.

Run a checked-in smoke request:

```bash
kubectl apply -f deploy/kubernetes/mocdiscoveryrequest-gaia-smoke-20260828.yaml
kubectl -n atlas-warehouse get mocdiscoveryrequest gaia-moc-discovery-smoke-20260828 -o yaml
kubectl -n atlas-warehouse get job -l atlas.zhejianglab.org/moc-discovery=true
```

The four public survey requests for SkyMapper DR4, KiDS DR5, VISTA VIKING J,
and DECaLS DR5 are recorded in
[`public-survey-coverage.md`](public-survey-coverage.md). Assets owns source
review, MOC generation, attribution, release manifests, and public publication.
