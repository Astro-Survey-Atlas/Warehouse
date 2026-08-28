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

# ADR-0012: Explicit Namespaced Operator Scope

## Status

Accepted

## Decision

One Operator Deployment may serve multiple caller namespaces, but only through
an explicit, non-empty `WATCH_NAMESPACES` allowlist. Fabric8 watches and lists
each namespace separately. The Helm chart renders one Role and RoleBinding per
entry, and generated Jobs, plan ConfigMaps, evidence mounts, and owner
references stay in the request namespace.

An empty or missing allowlist is a configuration error. The Operator never
falls back to `inAnyNamespace()` or a cluster-wide workload permission.

## Context

Assets and Workspace are independent callers and may submit requests to
different namespaces. A single cluster-scoped watcher would make an accidental
namespace typo a broad privilege escalation and would blur resource ownership.
Kubernetes owner references are namespace-local, so independent namespace
scopes also provide a clear deletion and audit boundary.

## Consequences

- A new caller namespace is an intentional RBAC and storage change.
- The same request name in two namespaces produces independent Jobs.
- Removing a namespace from the allowlist stops new reconciliation there but
  does not delete its user resources.
- Cluster administrators can inspect and audit each RoleBinding separately.
