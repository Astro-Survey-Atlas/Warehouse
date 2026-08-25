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
