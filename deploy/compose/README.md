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

# Local Docker Compose

This profile validates the Warehouse dependency and read/write loop on one
machine. It is not a Kubernetes or Operator simulator. Production
multi-namespace reconciliation still uses the Helm Operator chart.

## Start Dependencies

```bash
cp deploy/compose/.env.example deploy/compose/.env
docker compose --env-file deploy/compose/.env \
  -f deploy/compose/compose.yaml up -d
docker compose --env-file deploy/compose/.env \
  -f deploy/compose/compose.yaml ps
```

Elasticsearch is available at `http://127.0.0.1:9200`; MinIO API and console
are at ports `9000` and `9001`. The `index-bootstrap` one-shot service installs
strict mappings and creates the three `ast_*` indices idempotently.

## Run Query API Or A Scanner

The query and scanner images are published release artifacts. Start the query
profile after the bootstrap completes:

```bash
docker compose --env-file deploy/compose/.env \
  -f deploy/compose/compose.yaml --profile query up -d query-api
```

To run a local scanner, place a credential-free ScanPlan at
`deploy/compose/plans/scan-plan.json`, set the source/sink references in that
plan, and run:

```bash
docker compose --env-file deploy/compose/.env \
  -f deploy/compose/compose.yaml --profile scanner run --rm scanner
```

The scanner writes evidence to the named `evidence-data` volume. Use
`docker compose ... cp` or a temporary helper container to inspect it; it is
not returned by the public query API. For extractor development, use the
scanner `--memory` command in the root README instead of granting the Compose
container remote credentials.

## Optional Kafka

Kafka is not required by the current Scanner/Operator path. Enable it only for
an explicitly tested future event-driven or Flink experiment:

```bash
docker compose --env-file deploy/compose/.env \
  -f deploy/compose/compose.yaml --profile kafka up -d kafka
```

No current service publishes or consumes Warehouse scan events. Enabling this
profile alone does not change ScanPlan semantics.

## Stop And Reset

```bash
docker compose --env-file deploy/compose/.env \
  -f deploy/compose/compose.yaml down
```

`down` keeps named volumes. Add `-v` only for a disposable local reset; it
deletes local Elasticsearch, MinIO, Kafka, and evidence data.
