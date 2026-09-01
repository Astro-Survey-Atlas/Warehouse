#!/usr/bin/env bash
# Copyright 2026 Astro Survey Atlas contributors.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
canonical_dir="$repo_root/contracts/index"
compose_dir="$repo_root/deploy/compose/mappings"
helm_dir="$repo_root/deploy/helm/atlas-warehouse-infra/mappings"

check_only=false
if [[ "${1:-}" == "--check" ]]; then
  check_only=true
elif [[ "${1:-}" != "" ]]; then
  printf 'usage: %s [--check]\n' "$0" >&2
  exit 2
fi

for name in layer file coverage; do
  source="$canonical_dir/${name}-v1.json"
  for destination in "$compose_dir/${name}.json" "$helm_dir/${name}-v1.json"; do
    if [[ "$check_only" == true ]]; then
      cmp -s "$source" "$destination" || {
        printf 'index mapping drift: %s differs from %s\n' "$destination" "$source" >&2
        exit 1
      }
    else
      cp "$source" "$destination"
    fi
  done
done

if [[ "$check_only" == true ]]; then
  printf 'index mappings are synchronized\n'
fi
