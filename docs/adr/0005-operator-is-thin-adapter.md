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

# ADR-0005: Keep Operator Logic Thin

## Status

Accepted

## Decision

Keep a Kubernetes Operator in the product, but make it a thin adapter around the canonical ScanPlan. The Operator creates and observes scanner Jobs; it does not perform scientific processing or direct Elasticsearch I/O in reconcile callbacks.

## Context

Users may need Kubernetes-native submission and execution isolation. Removing the Operator would discard that integration, while placing scan logic inside reconcile callbacks would make retries, timeouts, and tests difficult to reason about. The scanner and spatial core must remain usable from a CLI and other callers.

## Consequences

- Kubernetes integration can evolve independently from FITS, catalog, and spatial algorithms.
- Scanner behavior can be tested locally and in a Job with the same plan input.
- Operator status must summarize an external Job rather than pretending to own every scientific detail.
- CRD design waits until the ScanPlan contract is stable, but the Operator remains a committed product module.
