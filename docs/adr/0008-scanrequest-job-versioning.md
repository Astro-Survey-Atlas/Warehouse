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

# ADR-0008: Version Scanner Jobs By Rendered Plan

## Status

Accepted

## Decision

The Operator stores the secret-free rendered plan in an immutable ConfigMap and
names the ConfigMap and scanner Job with an identity derived from the rendered
plan, credential binding references, and scanner execution settings. A repeated
reconcile of the same ScanRequest therefore observes the same resources. A
changed execution input creates a new Job rather than attempting to mutate an
immutable Job's Pod template. During Operator upgrades, an existing Job with
the same rendered plan hash and scanner image is treated as the equivalent
execution even if an older Operator serialized the execution settings
differently; active work is adopted and a successful duplicate is preferred
over a stale failed duplicate.

## Context

Kubernetes Jobs do not provide a safe in-place update model for their Pod
template. Reusing one Job name would make a changed ScanRequest ambiguous and
would either require destructive deletion or leave the old workload running
under new intent. A plan hash gives the status a precise execution identity and
keeps old runs inspectable until the configured Job TTL or owner deletion.

## Consequences

- Plan ConfigMaps are immutable and contain no credential values.
- A changed plan may leave more than one historical Job temporarily present.
- The initial Operator does not cancel an in-flight old Job when a plan changes.
- Operator upgrades do not abandon equivalent in-flight or successful Jobs;
  Jobs are selected by request ownership, rendered plan hash, and scanner
  image, with non-terminal work and success taking precedence over failures.
- Cleanup is delegated to owner references and `ttlSecondsAfterFinished`; an
  explicit cancellation policy can be added later without changing scan
  semantics.
