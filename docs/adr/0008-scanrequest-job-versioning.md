# ADR-0008: Version Scanner Jobs By Rendered Plan

## Status

Accepted

## Decision

The Operator stores the secret-free rendered plan in an immutable ConfigMap and
names the ConfigMap and scanner Job with an identity derived from the rendered
plan, credential binding references, and scanner execution settings. A repeated
reconcile of the same ScanRequest therefore observes the same resources. A
changed execution input creates a new Job rather than attempting to mutate an
immutable Job's Pod template.

## Context

Kubernetes Jobs do not provide a safe in-place update model for their Pod
template. Reusing one Job name would make a changed ScanRequest ambiguous and
would either require destructive deletion or leave the old workload running
under new intent. A plan hash gives the status a precise execution identity and
keeps old runs inspectable until the configured Job TTL or owner deletion.

## Consequences

- Plan ConfigMaps are immutable and contain no credential values.
- A changed plan may leave more than one historical Job temporarily present.
- The initial Operator does not cancel an in-flight old Job when a plan changes.
- Cleanup is delegated to owner references and `ttlSecondsAfterFinished`; an
  explicit cancellation policy can be added later without changing scan
  semantics.
