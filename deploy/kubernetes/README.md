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

# Kubernetes Examples

Helm is the only supported installer for Warehouse infrastructure, Operator
RBAC, Deployments, and CRDs. Use [`../helm/README.md`](../helm/README.md) to
install the `atlas-warehouse-infra` and `atlas-warehouse-operator` charts.
Those charts create the namespace-scoped `ScanRequest` and
`MocDiscoveryRequest` CRDs. Do not apply static controller or CRD manifests.

This directory contains credential-free examples to submit after Helm is
installed:

```text
kubectl apply -f deploy/kubernetes/evidence-pvc.example.yaml
kubectl apply -f deploy/kubernetes/scanrequest-local.example.yaml
kubectl apply -f deploy/kubernetes/scanrequest-oss.example.yaml
kubectl apply -f deploy/kubernetes/mocdiscoveryrequest.example.yaml
```

Edit the example plans for the target bucket, prefix, endpoints, and Secret
names. Keep credentials in namespace-local Secrets; they are referenced by
name and never stored in a plan, ConfigMap, Job log, evidence document, or
query response. `scanrequest-local.example.yaml` expects a read-only PVC named
`atlas-source-catalogs` carrying the
`atlas.zhejianglab.org/scanner-source=true` label. Declare that PVC through the
infra chart's `sourceVolumes` values or provision it separately.

The evidence PVC must be in the same namespace as its request. The Operator
validates the PVC, materializes an immutable plan ConfigMap, creates one
scanner or discovery Job, and reports status on the request. See
[`../../docs/operator.md`](../../docs/operator.md) for lifecycle and failure
semantics.

The examples are intentionally bounded and contain no private registry names,
live endpoints, dated retry identifiers, or credentials. Kafka remains an
optional Helm/Compose profile for future event-driven or Flink deployments; it
is not required by the current scanner path.
