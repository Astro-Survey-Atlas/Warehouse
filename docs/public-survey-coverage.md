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

# Public Survey Coverage Evidence

Warehouse records discovery evidence; it does not publish public survey MOCs.
The reviewed MOC files, previews, statistics, provenance, and release manifests
belong to [Assets](https://github.com/Astro-Survey-Atlas/Assets).

## Bounded Discovery Results

| Survey/product intent | Policy | Discovery result | Interpretation |
| --- | --- | --- | --- |
| SkyMapper DR4 color footprint | `cds-public-moc-v1` | CDS ObsCore HTTP 200, empty body; `candidateCount=0`, `probeCount=0` | Evidence of a bounded empty response, not absence of the survey MOC |
| KiDS DR5 color footprint | `cds-public-moc-v1` | CDS ObsCore HTTP 200, empty body; `candidateCount=0`, `probeCount=0` | Requires the Assets source snapshot for publication |
| VISTA VIKING J footprint | `cds-public-moc-v1` | CDS ObsCore HTTP 200, empty body; `candidateCount=0`, `probeCount=0` | Keep the upstream response and query parameters as evidence |
| DECaLS DR5 color footprint | `cds-public-moc-v1` | CDS ObsCore HTTP 200, empty body; `candidateCount=0`, `probeCount=0` | Do not manufacture geometry from a center or area estimate |
| Gaia DR3 smoke intent | `cds-public-moc-v1` | Bounded smoke request retained with policy and response evidence | A discovery probe is not a public release decision |

The current Assets worktree contains reviewed layer artifacts named
`skymapper-dr4-color-footprint`, `kids-dr5-color-footprint`,
`vista-viking-j-footprint`, and `decals-dr5-color-footprint`. Their publication
state and release hash are authoritative only after an explicit Assets release
or reload. Warehouse must not duplicate those MOC binaries or silently turn
discovery evidence into online coverage.

## Evidence Rules

- Record survey/release/product intent, policy ID, request URI, HTTP status, response hash, and timestamps.
- Keep candidate/probe counts and truncation separate from coverage precision.
- Never store credentials, unbounded response bodies, or scientific payloads in a browser response.
- Link to the Assets artifact and provenance record once the source is reviewed.
