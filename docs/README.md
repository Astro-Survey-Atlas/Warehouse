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

# Warehouse Documentation

Read the documents in this order when evaluating or operating Warehouse:

1. [`../README.md`](../README.md) or [`../README.cn.md`](../README.cn.md) for the product and user paths.
2. [`architecture.md`](architecture.md) for components, namespaces, ownership, and asynchronous execution.
3. [`project-boundary.md`](project-boundary.md) and [`requirements.md`](requirements.md) for ownership and v1 rules.
4. [`scan-plan.md`](scan-plan.md), [`index-contract.md`](index-contract.md), [`query-api.md`](query-api.md), and [`operator.md`](operator.md) for stable interfaces.
5. [`moc-discovery.md`](moc-discovery.md), [`public-survey-coverage.md`](public-survey-coverage.md), and [`sourceunit-roadmap.md`](sourceunit-roadmap.md) for evidence and future scope.

## Audience Map

| Audience | Entry point |
| --- | --- |
| End user | [`../deploy/helm/README.md`](../deploy/helm/README.md) or [`../deploy/compose/README.md`](../deploy/compose/README.md) |
| Kubernetes operator | [`operator.md`](operator.md) and [`../deploy/kubernetes/README.md`](../deploy/kubernetes/README.md) |
| Asset/Workspace integrator | [`cross-repo-contract.md`](cross-repo-contract.md) |
| Contributor | [`../CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Release maintainer | [`../RELEASING.md`](../RELEASING.md) |

The `docs/adr/` directory records decisions that are hard to reverse or would
otherwise be surprising. Contract changes must update the relevant document
and ADR in the same change. The current deployment decisions are
[`0012-namespaced-operator-scope.md`](adr/0012-namespaced-operator-scope.md),
[`0013-separate-operator-and-infrastructure-charts.md`](adr/0013-separate-operator-and-infrastructure-charts.md),
and [`0014-optional-event-driven-profile.md`](adr/0014-optional-event-driven-profile.md).
