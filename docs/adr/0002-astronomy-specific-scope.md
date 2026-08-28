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

# ADR-0002: Keep The Product Astronomy-Specific

## Status

Accepted

## Decision

Optimize the project for astronomical file discovery and spatial indexing. Do not introduce generic ProcessingTask, workflow, DAG, ETL, plugin, or data-catalog abstractions in the MVP.

## Context

A generic processing platform would overlap with established workflow and data catalog products while diluting the product's unique value. The useful user question is spatial: which astronomical files and modalities cover this sky position or region?

## Consequences

- The domain vocabulary stays small and astronomy-specific.
- FITS/WCS, catalog coordinates, HEALPix, FileAsset, and SpatialCoverage receive first-class design attention.
- Generic data sources, arbitrary user code, and unrelated data governance are explicit non-goals.
- Future generalization requires evidence from a real astronomy use case rather than speculative abstractions.
