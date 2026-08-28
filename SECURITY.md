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

# Security Policy

## Supported Versions

Only the latest tagged release and the current default branch receive security
fixes. Container images should be rebuilt from a supported release rather than
patched in place.

## Reporting A Vulnerability

Do not open a public issue for an undisclosed vulnerability. Use the private
security reporting channel configured on the
[Warehouse repository](https://github.com/Astro-Survey-Atlas/Warehouse/security)
or contact the maintainers through the organization profile. Include affected
version, deployment profile, reproduction steps, impact, and a suggested
mitigation when available.

The maintainers acknowledge reports within five business days, coordinate a
fix and advisory, and credit reporters unless they request anonymity. Do not
include credentials, private source URLs, or raw astronomy data in a report.

## Operational Security

- Keep source/sink credentials in namespace-local Kubernetes Secrets or an
  external secret manager; never put values in ScanPlan or evidence.
- Use TLS and authentication for production Elasticsearch, MinIO, and registries.
- Treat evidence and source inventories as potentially sensitive operational data.
- Review image, chart, Maven dependency, and SBOM findings before release.
- Do not upgrade or delete a running long scan without reviewing its Job and
  layer lease impact.
