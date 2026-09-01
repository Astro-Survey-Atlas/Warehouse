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

namespace="${WAREHOUSE_NAMESPACE:-atlas-warehouse}"
evidence_claim="${EVIDENCE_CLAIM:-atlas-evidence-smoke}"
source_claim="${SOURCE_CLAIM:-atlas-source-catalogs}"
s3_secret="${S3_SECRET_NAME:-assets-atlas-minio-smoke-credentials}"
s3_endpoint="${S3_ENDPOINT:-http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000}"
s3_bucket="${S3_BUCKET:-astro-artifacts}"
s3_prefix="${S3_PREFIX:-astro/smoke/assets-four-modalities/gaia-catalog-49cf5d4b.csv}"
es_endpoint="${ES_ENDPOINT:-http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200}"
scanner_image="${SCANNER_IMAGE:-}"
curl_image="${CURL_IMAGE:-curlimages/curl:8.10.1}"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-900}"
run_id="$(date -u +%Y%m%d%H%M%S)"

s3_request="warehouse-selftest-s3-${run_id}"
local_request="warehouse-selftest-local-${run_id}"
moc_request="warehouse-selftest-moc-${run_id}"
s3_layer="warehouse-selftest-s3"
local_layer="warehouse-selftest-local"

die() {
  printf 'warehouse smoke test: %s\n' "$*" >&2
  exit 2
}

command -v kubectl >/dev/null || die "kubectl is required"
[[ "$timeout_seconds" =~ ^[0-9]+$ ]] || die "SMOKE_TIMEOUT_SECONDS must be an integer"
(( timeout_seconds > 0 )) || die "SMOKE_TIMEOUT_SECONDS must be positive"

kubectl get namespace "$namespace" >/dev/null
kubectl -n "$namespace" get pvc "$evidence_claim" >/dev/null
kubectl -n "$namespace" get pvc "$source_claim" >/dev/null
kubectl -n "$namespace" get secret "$s3_secret" >/dev/null

scanner_image_yaml=""
if [[ -n "$scanner_image" ]]; then
  scanner_image_yaml="    image: ${scanner_image}"
fi

kubectl -n "$namespace" apply -f - <<YAML
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: ScanRequest
metadata:
  name: ${s3_request}
  namespace: ${namespace}
  labels:
    atlas.zhejianglab.org/self-test: warehouse-smoke
spec:
  scanner:
${scanner_image_yaml}
    activeDeadlineSeconds: 900
    backoffLimit: 0
    ttlSecondsAfterFinished: 3600
    evidence:
      claimName: ${evidence_claim}
      mountPath: /var/lib/atlas-evidence
  credentials:
    source:
      secretName: ${s3_secret}
      accessKeyKey: accessKey
      secretKeyKey: secretKey
  plan:
    version: 2
    scanRunId: ${s3_request}
    layer:
      layerId: ${s3_layer}
      surveyId: gaia
      releaseId: selftest
      productId: catalog
      modality: catalog
      coverageRole: occupancy
    source:
      connector:
        type: s3
        endpoint: ${s3_endpoint}
        region: us-east-1
        credentialRef:
          accessKeyEnv: ATLAS_SOURCE_ACCESS_KEY
          secretKeyEnv: ATLAS_SOURCE_SECRET_KEY
      location:
        bucket: ${s3_bucket}
        prefix: ${s3_prefix}
    filters:
      includeSuffixes: [.csv]
    extraction:
      mode: catalog-radec
      outputOrder: 8
      catalog:
        raColumn: ra
        decColumn: dec
    sink:
      connector:
        type: elasticsearch
        endpoint: ${es_endpoint}
        credentialRef: {}
    evidence:
      outputPath: /var/lib/atlas-evidence/${s3_request}
---
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: ScanRequest
metadata:
  name: ${local_request}
  namespace: ${namespace}
  labels:
    atlas.zhejianglab.org/self-test: warehouse-smoke
spec:
  scanner:
${scanner_image_yaml}
    activeDeadlineSeconds: 900
    backoffLimit: 0
    ttlSecondsAfterFinished: 3600
    evidence:
      claimName: ${evidence_claim}
      mountPath: /var/lib/atlas-evidence
    sourceVolume:
      claimName: ${source_claim}
      mountPath: /data
  plan:
    version: 2
    scanRunId: ${local_request}
    layer:
      layerId: ${local_layer}
      surveyId: desi
      releaseId: selftest
      productId: merger-samples
      modality: catalog
      coverageRole: occupancy
    source:
      connector:
        type: local
      location:
        rootPath: /data/gz_desi_merger_samples.csv
    filters:
      includeSuffixes: [.csv]
    extraction:
      mode: catalog-radec
      outputOrder: 8
      catalog:
        raColumn: ra
        decColumn: dec
    sink:
      connector:
        type: elasticsearch
        endpoint: ${es_endpoint}
        credentialRef: {}
    evidence:
      outputPath: /var/lib/atlas-evidence/${local_request}
---
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: MocDiscoveryRequest
metadata:
  name: ${moc_request}
  namespace: ${namespace}
  labels:
    atlas.zhejianglab.org/self-test: warehouse-smoke
spec:
  policyRef: cds-public-moc-v2
  query:
    surveyName: Gaia
YAML

wait_for_terminal() {
  local kind="$1"
  local name="$2"
  local started
  local phase=""
  started="$(date +%s)"
  while true; do
    phase="$(kubectl -n "$namespace" get "$kind" "$name" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    case "$phase" in
      SUCCEEDED|FAILED|INVALID)
        printf '%s %s phase=%s\n' "$kind" "$name" "$phase"
        return 0
        ;;
    esac
    if (( $(date +%s) - started >= timeout_seconds )); then
      printf '%s %s timed out after %ss (phase=%s)\n' "$kind" "$name" "$timeout_seconds" "${phase:-PENDING}" >&2
      return 1
    fi
    sleep 5
  done
}

print_scan_status() {
  local name="$1"
  kubectl -n "$namespace" get scanrequest "$name" \
    -o jsonpath='phase={.status.phase} reason={.status.reason} job={.status.jobName} discovered={.status.summary.discoveredFileCount} coverage={.status.summary.coverageRecordCount} errors={.status.summary.errorCount} snapshot={.status.summary.sourceSnapshotSha256} evidence={.status.summary.evidencePath}'
  printf '\n'
}

print_moc_status() {
  local name="$1"
  kubectl -n "$namespace" get mocdiscoveryrequest "$name" \
    -o jsonpath='phase={.status.phase} reason={.status.reason} job={.status.jobName} candidates={.status.candidateCount} evidence={.status.evidencePath}'
  printf '\n'
}

check_scan_summary() {
  local name="$1"
  local job evidence snapshot discovered coverage errors
  job="$(kubectl -n "$namespace" get scanrequest "$name" -o jsonpath='{.status.jobName}')"
  evidence="$(kubectl -n "$namespace" get scanrequest "$name" -o jsonpath='{.status.summary.evidencePath}')"
  snapshot="$(kubectl -n "$namespace" get scanrequest "$name" -o jsonpath='{.status.summary.sourceSnapshotSha256}')"
  discovered="$(kubectl -n "$namespace" get scanrequest "$name" -o jsonpath='{.status.summary.discoveredFileCount}')"
  coverage="$(kubectl -n "$namespace" get scanrequest "$name" -o jsonpath='{.status.summary.coverageRecordCount}')"
  errors="$(kubectl -n "$namespace" get scanrequest "$name" -o jsonpath='{.status.summary.errorCount}')"
  if [[ -z "$job" || -z "$evidence" || -z "$snapshot" ||
    ! "$discovered" =~ ^[1-9][0-9]*$ || ! "$coverage" =~ ^[1-9][0-9]*$ ||
    "$errors" != 0 ]]; then
    printf 'scan summary invalid for %s: job=%s evidence=%s snapshot=%s discovered=%s coverage=%s errors=%s\n' \
      "$name" "$job" "$evidence" "$snapshot" "$discovered" "$coverage" "$errors" >&2
    return 1
  fi
}

check_moc_summary() {
  local name="$1"
  local job evidence candidates
  job="$(kubectl -n "$namespace" get mocdiscoveryrequest "$name" -o jsonpath='{.status.jobName}')"
  evidence="$(kubectl -n "$namespace" get mocdiscoveryrequest "$name" -o jsonpath='{.status.evidencePath}')"
  candidates="$(kubectl -n "$namespace" get mocdiscoveryrequest "$name" -o jsonpath='{.status.candidateCount}')"
  if [[ -z "$job" || -z "$evidence" || ! "$candidates" =~ ^[1-9][0-9]*$ ]]; then
    printf 'MOC summary invalid for %s: job=%s evidence=%s candidates=%s\n' \
      "$name" "$job" "$evidence" "$candidates" >&2
    return 1
  fi
}

check_index_layers() {
  local check_pod="warehouse-selftest-es-${run_id}"
  local check_script
  check_script=$(cat <<EOF
set -eu
for layer in ${s3_layer} ${local_layer}; do
  response="\$(curl -fsS -G "${es_endpoint}/ast_layer_index_v1/_count" --data-urlencode "q=layer_id:\$layer AND state:ACTIVE")"
  count="\$(printf '%s' "\$response" | sed -n 's/.*"count":\([0-9][0-9]*\).*/\1/p')"
  test -n "\$count" && test "\$count" -gt 0
  printf '%s index-count=%s\\n' "\$layer" "\$count"
done
EOF
)
  kubectl -n "$namespace" run "$check_pod" --rm -i --restart=Never \
    --image="$curl_image" --command -- sh -ec "$check_script"
}

result=0
wait_for_terminal scanrequest "$s3_request" || result=1
wait_for_terminal scanrequest "$local_request" || result=1
wait_for_terminal mocdiscoveryrequest "$moc_request" || result=1

print_scan_status "$s3_request"
print_scan_status "$local_request"
print_moc_status "$moc_request"

[[ "$(kubectl -n "$namespace" get scanrequest "$s3_request" -o jsonpath='{.status.phase}')" == SUCCEEDED ]] || result=1
[[ "$(kubectl -n "$namespace" get scanrequest "$local_request" -o jsonpath='{.status.phase}')" == SUCCEEDED ]] || result=1
[[ "$(kubectl -n "$namespace" get mocdiscoveryrequest "$moc_request" -o jsonpath='{.status.phase}')" == SUCCEEDED ]] || result=1

if [[ "$result" == 0 ]]; then
  check_scan_summary "$s3_request" || result=1
  check_scan_summary "$local_request" || result=1
  check_moc_summary "$moc_request" || result=1
fi

if [[ "$result" == 0 ]]; then
  check_index_layers || result=1
fi

if [[ "$result" != 0 ]]; then
  printf 'warehouse smoke test: FAILED\n' >&2
  exit 1
fi
printf 'warehouse smoke test: PASSED (requests retained: %s, %s, %s)\n' \
  "$s3_request" "$local_request" "$moc_request"
