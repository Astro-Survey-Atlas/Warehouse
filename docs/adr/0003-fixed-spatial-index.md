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
