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

# Contributing

Astro Survey Atlas Warehouse is developed in the open in the
[Astro Survey Atlas organization](https://github.com/Astro-Survey-Atlas). It
uses Apache-2.0 and a review-first workflow. It is not governed by the Apache
Software Foundation or an ASF PMC.

## Before Opening A Change

1. Read [`AGENTS.md`](AGENTS.md), [`CONTEXT.md`](CONTEXT.md), and the relevant
   contract under [`docs/`](docs/README.md).
2. For a behavior change, describe the user-visible contract and failure mode
   in the issue before implementing it.
3. Keep changes scoped to the owning module. Do not edit the frozen
   `/home/aaron/Repo/data-warehouse` checkout or use `astro_*` as a fallback.
4. Never include credentials, signed URLs, raw astronomy payloads, or local
   machine paths in a plan, evidence fixture, log, or pull request.

## Development Checks

```bash
mvn -B verify
helm lint deploy/helm/atlas-warehouse-infra
helm lint deploy/helm/atlas-warehouse-operator
docker compose -f deploy/compose/compose.yaml config
git diff --check
```

Add tests at the highest useful public interface. Contract changes must update
the corresponding Markdown contract and an ADR when the decision is hard to
reverse or surprising. Tests must cover invalid input before I/O, normal and
failure behavior, evidence/precision invariants, and namespace ownership when
applicable.

## Pull Requests

Pull requests should state the problem, contract change, compatibility impact,
tests run, deployment impact, and rollback plan. Keep commits reviewable. A
maintainer may request a cross-repository review from Assets or Workspace when
their contract is affected.

Contributors certify each commit with a DCO sign-off:

```text
Signed-off-by: Your Name <you@example.com>
```

The repository does not require a CLA today. Contributions are accepted under
Apache-2.0 when submitted for inclusion, subject to the contributor's DCO
sign-off and the project's review process.
