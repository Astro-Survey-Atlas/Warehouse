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
