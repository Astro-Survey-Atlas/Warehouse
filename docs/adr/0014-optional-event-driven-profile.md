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

# ADR-0014: Keep Event-Driven Processing Optional

## Status

Accepted

## Decision

The v1 scanner executes bounded source enumeration, extraction, evidence, and
Elasticsearch writes directly inside a Kubernetes Job or local Compose
process. Kafka is an optional, disabled-by-default infrastructure profile.
Flink is not installed by the default charts. A future event-driven profile
may enable Kafka and Flink only after a separately reviewed event contract,
delivery semantics, replay policy, and evidence ownership are published.

Enabling Kafka alone does not change `ScanPlan`, `ScanRequest`, layer lease,
coverage precision, or failure semantics.

## Context

Local verification and small bounded scans should not require a broker or
stream processor. At the same time, larger deployments may eventually need
decoupled scheduling or parallel processing. Making those components optional
keeps the early path observable and avoids implying that a broker is already a
runtime dependency.

## Consequences

- Compose and the default Helm profile remain usable on a single machine.
- Operators must explicitly review broker state before an infrastructure
  upgrade or rollback.
- Future producers and consumers must preserve the current domain contracts;
  they cannot smuggle arbitrary workflows or handler order into `ScanPlan`.
- Documentation must label Kafka/Flink as optional until the event contract is
  accepted.
