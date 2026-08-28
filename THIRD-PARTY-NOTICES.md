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

# Third-Party Notices

This repository contains or references third-party components. The release
workflow must regenerate the dependency report and SBOM for every version.
This file is a human-readable summary, not a replacement for each component's
license text.

| Component | Use | License/source |
| --- | --- | --- |
| Elasticsearch | Search service and Java client protocol | Apache-2.0 for the client; Elasticsearch distribution terms apply to the selected image |
| Apache Kafka | Optional future event transport | Apache-2.0; vendored Bitnami chart and image attribution required |
| MinIO | Optional bundled evidence/object storage | MinIO server image is AGPL-3.0; verify deployment and redistribution obligations before publishing a bundle |
| Bitnami Elasticsearch/Kafka/MinIO charts | Vendored Helm dependencies | Apache-2.0 chart licenses; retain upstream notices and chart metadata |
| Jackson, AWS SDK, Fabric8 Kubernetes Client, JUnit | Java dependencies | See generated CycloneDX SBOM and Maven dependency report |
| HEALPix/scientific libraries | Spatial extraction and tests | See module POMs and generated SBOM |

The release job fails if a dependency has an unknown license, a prohibited
license for the selected distribution, or missing attribution. Vendored chart
directories are third-party source and are excluded from project-style header
checks only when their upstream notices remain present.
