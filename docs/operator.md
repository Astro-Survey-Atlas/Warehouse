# Operator Contract

## Resource

The Kubernetes adapter watches the namespaced `ScanRequest` resource:

```yaml
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: ScanRequest
metadata:
  name: survey-release-1
spec:
  plan: <canonical ScanPlan>
  credentials:
    source:
      secretName: atlas-source-credentials
      accessKeyKey: accessKey
      secretKeyKey: secretKey
    sink:
      secretName: atlas-elasticsearch-credentials
      usernameKey: username
      passwordKey: password
  scanner:
    image: astro-atlas-scanner:0.1.0
```

`spec.plan` is the same finite ScanPlan accepted by `scanner-cli`. The
Operator validates it with `spatial-core` before creating any Job. The
`credentials` section contains only Secret names and keys. It is not a
credential store and the Operator never reads Secret data.

## Reconciliation

For a valid request, the Operator:

1. Renders a secret-free `plan.json` into an immutable ConfigMap.
2. Creates a scanner Job owned by the ScanRequest.
3. Injects environment credential references with `env.valueFrom.secretKeyRef`.
4. Rewrites file credential references to read-only Secret volume paths.
5. Polls Job state and copies only the scanner's structured summary line into status.

The Job name includes a SHA-256 identity derived from the rendered plan,
credential binding references, and scanner execution settings. Reapplying the
same request is idempotent. Changing any of those inputs creates a new Job and
leaves the old Job available until its configured TTL. Deleting the ScanRequest lets
Kubernetes garbage-collect its owned ConfigMaps and Jobs.

After a terminal Job is removed by TTL, the terminal status prevents the same
execution from being recreated. A changed execution identity is treated as a
new run and creates a new Job.

The initial status phases are `INVALID`, `SUBMITTED`, `RUNNING`, `SUCCEEDED`,
and `FAILED`. Invalid plans and missing Secret bindings are reported in status
without attempting source or Elasticsearch access.

## Runtime Configuration

The Operator process reads:

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `WATCH_NAMESPACE` | empty | Empty watches all namespaces; otherwise one namespace |
| `SCANNER_IMAGE` | `ghcr.io/zhejianglab/astro-survey-atlas-scanner:0.1.0` | Default Job image |
| `RECONCILE_INTERVAL_SECONDS` | `10` | Job status polling interval |

The deployment uses a ClusterRole because the checked-in default watches all
namespaces. A namespaced Role/RoleBinding can be substituted when
`WATCH_NAMESPACE` is set.

## Deliberate Limits

The Operator submits one finite scanner Job. It does not implement schedules,
DAGs, arbitrary container commands, local host-path mounts, scientific
processing, or direct Elasticsearch requests. The scanner image contract is a
Java 17 image containing `/app/scanner-cli.jar`.
