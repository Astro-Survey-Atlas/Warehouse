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

# ADR-0015: Make Extraction Failures Terminal

## Status

Accepted

## Decision

One persisted scanner execution stops at the first extraction error. The
scanner records the affected FileAsset and error in Evidence, stops enumerating
later files, and marks the CoverageLayer `FAILED`; it never skips the bad input
and publishes a partial `ACTIVE` layer. Catalog extraction stops at the first
malformed spatial row for the same reason. Empty lines, comments, and objects
filtered by the declared suffix/exclude rules remain normal filtering.

Scanner and MOC discovery Jobs use `backoffLimit=0`. Retrying is an explicit
new ScanRequest or MocDiscoveryRequest and execution identity after the caller
reviews Evidence. S3, Elasticsearch, and allowlisted discovery HTTP calls may
retry only within their adapter/policy-specific bounded limits and timeouts;
transport retries do not change extraction semantics.

## Context

Previously, an extraction error was accumulated while enumeration continued,
and the default Kubernetes Job could start a second full attempt. A deterministic
bad FITS header or catalog row therefore produced misleading long-running Jobs,
duplicate source reads, and a final `DeadlineExceeded` instead of an immediate
failure. Continuing also made it harder to distinguish a complete empty scan
from a partial scan.

## Consequences

- Evidence and the layer state identify the first actionable failure quickly.
- A failed run may leave bounded physical index writes, but its layer is never
  queryable because only `ACTIVE` layers are searchable.
- Callers must create a new execution to retry; changing a source or plan is
  visible in the request identity and audit trail.
- Transient object-store or Elasticsearch outages still receive bounded
  transport-level retries without replaying the entire scanner Job.
