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

# Governance

Astro Survey Atlas Warehouse is an open-source project hosted by the Astro
Survey Atlas GitHub organization. It follows Apache-style transparency and
merit-based review, but it is not an Apache Software Foundation project and
does not have an ASF PMC.

## Roles

- **Contributors** propose changes, maintain tests and documentation, and
  respond to review.
- **Reviewers** check behavior, contracts, security, operability, and backward
  compatibility. Approval is earned through sustained, high-quality work.
- **Maintainers** merge changes, manage releases, resolve technical disputes,
  and maintain the security response process.
- **Release manager** coordinates versioning, artifact provenance, SBOMs,
  dependency review, and the release vote/approval recorded in the repository.

The current maintainer list is maintained in the GitHub repository settings and
release metadata; project roles are not inferred from commit count alone.

## Decisions

Routine changes use pull-request review and consensus. A stable product rule,
public API, CRD field, index mapping, or deployment boundary requires an
explicit contract update and, when difficult to reverse, an ADR. If consensus
cannot be reached, maintainers record the alternatives and rationale in the
issue or ADR and make a time-bounded decision.

## Cross-Repository Changes

Changes affecting Assets or Workspace contracts require links to the impacted
repository issue/PR and a compatibility note. Each repository retains its own
ownership; no repository may silently import another's private implementation.
