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

# Project Boundary

Warehouse is the execution and current spatial-state boundary in the Astro
Survey Atlas organization. It is deliberately narrower than a workflow engine
or data lake.

## Ownership

| Area | Owner | Contract boundary |
| --- | --- | --- |
| ScanPlan/ScanRequest execution | Warehouse | Validate one finite plan and expose Job status |
| Source enumeration and extraction | Warehouse | Local, S3-compatible, and OSS metadata only |
| Current file and coverage state | Warehouse | `ast_layer_index_v1`, `ast_file_index_v1`, `ast_coverage_index_v1` |
| Inventory and extraction evidence | Warehouse | PVC/CSI/object-store evidence output, not browser startup data |
| Public survey catalog and MOCs | Assets | Reviewed source snapshots, MOCs, previews, manifests, provenance, and release |
| User assets and local workflows | Workspace | Local state, connectors, user labels, and optional remote scan submission |
| User-facing overlap and reverse lookup | Assets | Public UX; Warehouse provides current file-level evidence |
| Kubernetes translation | Warehouse Operator | Namespaced resources, Secret references, Jobs, and status only |

## Explicit Non-Ownership

- Warehouse does not own raw astronomy data, user downloads, source reduction,
  scientific arrays, archival, or a universal catalog.
- Warehouse does not provide arbitrary handlers, plugins, scripts, DAGs, or
  recurring workflow scheduling in v1.
- MOC discovery is evidence-only; it does not publish public geometry or write
  `ast_*` indices.
- Assets and Workspace do not receive credential values from Warehouse.
- No project modifies the frozen legacy checkout or uses `astro_*` as a runtime
  fallback.

## Namespace Boundary

One Operator may watch several explicitly allowlisted request namespaces (the
default Helm values watch only `atlas-warehouse`). A
request, its plan ConfigMap, Job, evidence PVC, and credential references stay
in the request namespace. The Operator ServiceAccount lives in the Helm release
namespace, but each watched namespace receives a separate least-privilege
Role/RoleBinding.
An empty allowlist is invalid; it never means "all namespaces".

## Module Rules

- `spatial-core` owns domain types and validation and knows no Kubernetes or
  transport lifecycle.
- `scanner-cli` owns enumeration, extraction, evidence, and index writes.
- `index-elasticsearch` owns leases, current-layer replacement, strict mappings,
  bounded writes, and multi-order reads.
- `query-api` owns read-only request validation and joins.
- `operator` owns Kubernetes translation and Job status only.
- `moc-discovery-cli` owns bounded public MOC discovery evidence only.
