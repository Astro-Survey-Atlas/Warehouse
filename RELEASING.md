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

# Release Process

This project uses semantic versioning for public APIs and chart mappings. The
Maven modules, container images, and Helm charts publish the same release
version unless a module-specific compatibility note says otherwise.

## Prepare

1. Confirm the product and contract changes are documented and all relevant
   ADRs are accepted.
2. Run `mvn -B verify`, `mvn -B package`, both Helm lint/template checks, and
   `docker compose -f deploy/compose/compose.yaml config`.
3. Run Apache RAT, dependency vulnerability/license checks, and generate a
   CycloneDX SBOM for source and runtime artifacts.
4. Build images with immutable tags/digests and verify non-root runtime settings.
5. Run bounded contract probes and record unsupported inputs as evidence.
6. Review active Kubernetes Jobs, layer leases, PVCs, and the upgrade/rollback
   plan. Never cancel a running scan as a side effect of a documentation or
   image release.

## Publish

The tagged GitHub Actions workflow publishes:

- source archive and checksums;
- Maven runner artifacts;
- scanner, Operator, query-api, and MOC discovery images;
- `atlas-warehouse-infra` and `atlas-warehouse-operator` Helm charts;
- SBOM, dependency/license report, and provenance metadata.

The release notes link the tag, source commit, chart/image digests, contract
changes, known limitations, migration notes, and security fixes. A release is
not complete until its checksums and SBOM are attached to the GitHub release.

## Rollback

Rollback the affected Helm release or image tag only after checking active Jobs
and index mapping compatibility. Preserve evidence and failed Jobs for
diagnosis. A chart rollback does not restore deleted PVC data or an older
current-state layer; recover those through a new explicit ScanRequest.
