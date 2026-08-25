# ADR-0004: Keep Indexed History And Use Stable URI IDs

## Status

Superseded by ADR-0009

## Decision

Derive FileAsset IDs from canonical source URI hashes, use deterministic coverage IDs, and retain indexed documents even when a later scan no longer finds the source file.

## Context

Rescans must be idempotent, and deleting search history because a remote listing changed would be surprising and potentially destructive. The MVP is a discovery index, not a source-state reconciler.

## Consequences

- Repeated scans upsert the same FileAsset.
- A later cleanup or tombstone policy can be designed explicitly if needed.
- Search results may include historical assets whose current source availability must be checked by a caller.
