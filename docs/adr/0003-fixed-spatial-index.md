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

# ADR-0003: Normalize The MVP To ICRS NESTED HEALPix Order 8

## Status

Superseded by ADR-0009

## Decision

Store and query spatial coverage using ICRS, NESTED HEALPix, fixed order 8. Convert point, cone, and requested HEALPix queries to that representation. Return coverage candidates and accept order-8 pixel-boundary false positives.

## Context

FITS WCS, catalog coordinates, and query inputs need one common representation. A fixed order makes the initial index and query contract predictable. Exact geometry refinement would add complexity without being necessary to validate the discovery workflow.

## Consequences

- The two index contracts share one cell representation.
- Query results are candidates rather than exact scientific containment claims.
- The index cannot express arbitrary order-specific detail in the MVP.
- A future higher-resolution or exact-geometry feature must introduce a new contract or an explicit versioned extension.
