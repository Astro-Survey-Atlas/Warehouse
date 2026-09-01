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

# Atlas Warehouse Operator Chart

This chart installs the thin Warehouse Operator and the two namespaced request
CRDs in the Helm release namespace, then grants the Operator least-privilege
access to the explicitly configured `watchNamespaces`. It does not install
Elasticsearch, MinIO, Kafka, or Flink; install
`atlas-warehouse-infra` separately for the self-managed dependencies.

## Install From OCI

```bash
helm upgrade --install atlas-warehouse-operator \
  oci://ghcr.io/astro-survey-atlas/charts/atlas-warehouse-operator \
  --version 0.1.1 --namespace atlas-system --create-namespace \
  --set 'watchNamespaces[0]=atlas-warehouse' \
  --wait
```

The same chart can be installed from a local `.tgz` package. The namespace list
is required and is rendered as one Role/RoleBinding pair per entry. An empty
list is rejected by the chart schema and the application also fails closed.

## Values

| Value | Meaning |
| --- | --- |
| `watchNamespaces` | Explicit request namespaces; never use an all-namespace scope |
| Helm release namespace | Namespace of the Operator ServiceAccount and Deployment |
| `scanner.image` | Default image for ScanRequest Jobs |
| `mocDiscovery.image` | Image for evidence-only MOC discovery Jobs |
| `mocDiscovery.evidenceClaim` | Namespace-local PVC name used by discovery Jobs |
| `operator.resources` | Operator pod requests and limits |

Adding a namespace is an intentional RBAC change: create its Evidence PVC and
credential policy, then upgrade this chart with the new allowlist. Removing a
namespace stops reconciliation there after the existing Job lifecycle is
handled; it does not delete user resources.
