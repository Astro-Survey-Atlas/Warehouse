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

caller="${CALLER:-asset}"
run_id="$(date -u +%Y%m%d%H%M%S)"
es_endpoint="${ES_ENDPOINT:-http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200}"
scanner_image="${SCANNER_IMAGE:-}"
curl_image="${CURL_IMAGE:-curlimages/curl:8.10.1}"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-900}"
keep_requests="${KEEP_REQUESTS:-0}"

die() {
  printf 'warehouse caller smoke test: %s\n' "$*" >&2
  exit 2
}

command -v kubectl >/dev/null || die "kubectl is required"
[[ "$timeout_seconds" =~ ^[0-9]+$ ]] || die "SMOKE_TIMEOUT_SECONDS must be an integer"
(( timeout_seconds > 0 )) || die "SMOKE_TIMEOUT_SECONDS must be positive"
[[ "$keep_requests" == 0 || "$keep_requests" == 1 ]] || die "KEEP_REQUESTS must be 0 or 1"

scanner_image_yaml=""
if [[ -n "$scanner_image" ]]; then
  scanner_image_yaml="    image: ${scanner_image}"
fi

case "$caller" in
  asset)
    namespace="${REQUEST_NAMESPACE:-atlas-warehouse}"
    evidence_claim="${EVIDENCE_CLAIM:-atlas-evidence-smoke}"
    source_secret="${S3_SECRET_NAME:-assets-atlas-minio-smoke-credentials}"
    source_access_key_key="${S3_ACCESS_KEY_KEY:-accessKey}"
    source_secret_key_key="${S3_SECRET_KEY_KEY:-secretKey}"
    source_endpoint="${S3_ENDPOINT:-http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000}"
    source_bucket="${S3_BUCKET:-astro-artifacts}"
    source_prefix="${S3_PREFIX:-astro/smoke/assets-four-modalities/gaia-catalog-49cf5d4b.csv}"
    scan_request="warehouse-caller-assets-scan-${run_id}"
    moc_request="warehouse-caller-assets-moc-${run_id}"
    layer_id="warehouse-caller-assets"
    source_labels=$'    app.kubernetes.io/managed-by: astro-survey-atlas-assets\n    atlas.zhejianglab.org/track-caller: assets\n    atlas.zhejianglab.org/track-task-kind: self-test'
    filters_yaml='      includeSuffixes: [.csv]'
    extraction_yaml=$'      mode: catalog-radec\n      outputOrder: 8\n      catalog:\n        raColumn: ra\n        decColumn: dec'
    layer_yaml=$'      surveyId: gaia\n      releaseId: caller-selftest\n      productId: catalog\n      modality: catalog\n      coverageRole: occupancy'
    kubectl get namespace "$namespace" >/dev/null
    kubectl -n "$namespace" get pvc "$evidence_claim" >/dev/null
    kubectl -n "$namespace" get secret "$source_secret" >/dev/null
    ;;
  workspace)
    namespace="${REQUEST_NAMESPACE:-astro-data-workspace}"
    evidence_claim="${EVIDENCE_CLAIM:-workspace-evidence}"
    source_secret="${WORKSPACE_SOURCE_SECRET:-}"
    source_access_key_key="${WORKSPACE_ACCESS_KEY_KEY:-access-key}"
    source_secret_key_key="${WORKSPACE_SECRET_KEY_KEY:-secret-key}"
    source_endpoint="${WORKSPACE_S3_ENDPOINT:-}"
    source_bucket="${WORKSPACE_S3_BUCKET:-data-and-computing}"
    source_prefix="${WORKSPACE_S3_PREFIX:-projects/CSST/shared-data/euclid/aws-mirrors/q1/MER/102018212/VIS/EUC_MER_BGMOD-VIS_TILE102018212-3293DE_20241018T201846.882386Z_00.00.fits}"
    scan_request="warehouse-caller-workspace-scan-${run_id}"
    layer_id="warehouse-caller-workspace"
    source_labels=$'    app.kubernetes.io/managed-by: astro-data-workspace\n    astro.zhejianglab.org/atlas-task: "true"\n    astro.zhejianglab.org/atlas-task-kind: self_test\n    astro.zhejianglab.org/track-caller: workspace\n    atlas.zhejianglab.org/track-caller: workspace\n    atlas.zhejianglab.org/track-task-kind: self-test'
    filters_yaml='      includeSuffixes: [.fits]'
    extraction_yaml=$'      mode: fits-wcs\n      outputOrder: 8'
    layer_yaml=$'      surveyId: euclid\n      releaseId: caller-selftest\n      productId: q1-vis-bgmod\n      modality: image\n      coverageRole: footprint\n      entrypoint: oss://'"${source_bucket}"'/'"${source_prefix}"
    kubectl get namespace "$namespace" >/dev/null
    kubectl -n "$namespace" get pvc "$evidence_claim" >/dev/null
    if [[ -z "$source_secret" ]]; then
      source_secret="$(kubectl -n "$namespace" get secret \
        -l 'astro.zhejianglab.org/connector-credential=true' \
        -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' | sort -u | sed -n '1p')" || \
        die "failed to discover a Workspace connector Secret"
      [[ -n "$source_secret" ]] || \
        die "no Secret labeled astro.zhejianglab.org/connector-credential=true in $namespace"
    fi
    kubectl -n "$namespace" get secret "$source_secret" >/dev/null
    if [[ -z "$source_endpoint" ]]; then
      encoded_endpoint="$(kubectl -n "$namespace" get secret "$source_secret" \
        -o jsonpath='{.data.s3-endpoint}')" || \
        die "failed to read s3-endpoint from Workspace connector Secret"
      [[ -n "$encoded_endpoint" ]] || \
        die "Workspace connector Secret $source_secret has no s3-endpoint key"
      source_endpoint="$(printf '%s' "$encoded_endpoint" | base64 --decode)" || \
        die "failed to decode s3-endpoint from Workspace connector Secret"
      [[ -n "$source_endpoint" ]] || \
        die "Workspace connector Secret $source_secret has an empty s3-endpoint"
    fi
    ;;
  *)
    die "CALLER must be asset or workspace"
    ;;
esac

kubectl -n "$namespace" apply -f - <<YAML
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: ScanRequest
metadata:
  name: ${scan_request}
  namespace: ${namespace}
  labels:
${source_labels}
    atlas.zhejianglab.org/self-test: warehouse-caller-smoke
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
      secretName: ${source_secret}
      accessKeyKey: ${source_access_key_key}
      secretKeyKey: ${source_secret_key_key}
  plan:
    version: 2
    scanRunId: ${scan_request}
    layer:
      layerId: ${layer_id}
${layer_yaml}
    source:
      connector:
        type: s3
        endpoint: ${source_endpoint}
        region: us-east-1
        credentialRef:
          accessKeyEnv: ATLAS_SOURCE_ACCESS_KEY
          secretKeyEnv: ATLAS_SOURCE_SECRET_KEY
      location:
        bucket: ${source_bucket}
        prefix: ${source_prefix}
    filters:
${filters_yaml}
    extraction:
${extraction_yaml}
    sink:
      connector:
        type: elasticsearch
        endpoint: ${es_endpoint}
        credentialRef: {}
    evidence:
      outputPath: /var/lib/atlas-evidence/${scan_request}
YAML

if [[ "$caller" == asset ]]; then
  kubectl -n "$namespace" apply -f - <<YAML
apiVersion: atlas.zhejianglab.org/v1alpha1
kind: MocDiscoveryRequest
metadata:
  name: ${moc_request}
  namespace: ${namespace}
  labels:
${source_labels}
    atlas.zhejianglab.org/self-test: warehouse-caller-smoke
spec:
  policyRef: cds-public-moc-v2
  query:
    surveyName: Gaia
YAML
  requests=("scanrequest/$scan_request" "mocdiscoveryrequest/$moc_request")
else
  requests=("scanrequest/$scan_request")
fi
layers=("$layer_id")

wait_for_terminal() {
  local resource="$1"
  local started phase=""
  started="$(date +%s)"
  while true; do
    phase="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    case "$phase" in
      SUCCEEDED|FAILED|INVALID)
        printf '%s phase=%s\n' "$resource" "$phase"
        return 0
        ;;
    esac
    if (( $(date +%s) - started >= timeout_seconds )); then
      printf '%s timed out after %ss (phase=%s)\n' "$resource" "$timeout_seconds" "${phase:-PENDING}" >&2
      return 1
    fi
    sleep 5
  done
}

print_status() {
  local resource="$1"
  case "$resource" in
    scanrequest/*)
      kubectl -n "$namespace" get "$resource" \
        -o jsonpath='phase={.status.phase} reason={.status.reason} job={.status.jobName} layer={.status.summary.layerId} discovered={.status.summary.discoveredFileCount} coverage={.status.summary.coverageRecordCount} errors={.status.summary.errorCount} snapshot={.status.summary.sourceSnapshotSha256} evidence={.status.summary.evidencePath}'
      ;;
    mocdiscoveryrequest/*)
      kubectl -n "$namespace" get "$resource" \
        -o jsonpath='phase={.status.phase} reason={.status.reason} job={.status.jobName} candidates={.status.candidateCount} truncated={.status.reviewSummary.truncated} evidence={.status.evidencePath}'
      ;;
  esac
  printf '\n'
}

check_layer() {
  local layer="$1"
  local check_pod="warehouse-caller-es-${caller}-${run_id}"
  local check_script
  check_script=$(cat <<EOF
set -eu
response="\$(curl -fsS -G "${es_endpoint}/ast_layer_index_v1/_count" --data-urlencode "q=layer_id:${layer} AND state:ACTIVE")"
count="\$(printf '%s' "\$response" | sed -n 's/.*"count":\([0-9][0-9]*\).*/\1/p')"
test -n "\$count" && test "\$count" -gt 0
printf '%s index-count=%s\\n' "${layer}" "\$count"
EOF
)
  kubectl -n "$namespace" run "$check_pod" --rm -i --restart=Never \
    --image="$curl_image" --command -- sh -ec "$check_script"
}

check_scan_summary() {
  local resource="$1"
  local job evidence snapshot discovered coverage errors
  job="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.jobName}')"
  evidence="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.summary.evidencePath}')"
  snapshot="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.summary.sourceSnapshotSha256}')"
  discovered="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.summary.discoveredFileCount}')"
  coverage="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.summary.coverageRecordCount}')"
  errors="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.summary.errorCount}')"
  if [[ -z "$job" || -z "$evidence" || -z "$snapshot" ||
    ! "$discovered" =~ ^[1-9][0-9]*$ || ! "$coverage" =~ ^[1-9][0-9]*$ ||
    "$errors" != 0 ]]; then
    printf 'scan summary invalid for %s: job=%s evidence=%s snapshot=%s discovered=%s coverage=%s errors=%s\n' \
      "$resource" "$job" "$evidence" "$snapshot" "$discovered" "$coverage" "$errors" >&2
    return 1
  fi
}

check_moc_summary() {
  local resource="$1"
  local job evidence candidates
  job="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.jobName}')"
  evidence="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.evidencePath}')"
  candidates="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.candidateCount}')"
  if [[ -z "$job" || -z "$evidence" || ! "$candidates" =~ ^[1-9][0-9]*$ ]]; then
    printf 'MOC summary invalid for %s: job=%s evidence=%s candidates=%s\n' \
      "$resource" "$job" "$evidence" "$candidates" >&2
    return 1
  fi
}

check_caller_labels() {
  local expected_managed expected_caller
  if [[ "$caller" == asset ]]; then
    expected_managed="astro-survey-atlas-assets"
    expected_caller="assets"
  else
    expected_managed="astro-data-workspace"
    expected_caller="workspace"
  fi
  for resource in "${requests[@]}"; do
    local managed caller_label
    managed="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"
    caller_label="$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.metadata.labels.atlas\.zhejianglab\.org/track-caller}')"
    [[ "$managed" == "$expected_managed" && "$caller_label" == "$expected_caller" ]] || {
      printf 'caller labels invalid for %s: managed=%s caller=%s\n' "$resource" "$managed" "$caller_label" >&2
      return 1
    }
  done
}

result=0
for resource in "${requests[@]}"; do
  wait_for_terminal "$resource" || result=1
done
for resource in "${requests[@]}"; do
  print_status "$resource"
done
for resource in "${requests[@]}"; do
  [[ "$(kubectl -n "$namespace" get "$resource" -o jsonpath='{.status.phase}')" == SUCCEEDED ]] || result=1
done
if [[ "$result" == 0 ]]; then
  check_caller_labels || result=1
fi
if [[ "$result" == 0 && "$caller" == asset ]]; then
  check_moc_summary "mocdiscoveryrequest/$moc_request" || result=1
fi
if [[ "$result" == 0 ]]; then
  for resource in "${requests[@]}"; do
    [[ "$resource" == scanrequest/* ]] || continue
    check_scan_summary "$resource" || result=1
  done
fi
if [[ "$result" == 0 ]]; then
  for layer in "${layers[@]}"; do
    check_layer "$layer" || result=1
  done
fi

if [[ "$result" != 0 ]]; then
  printf 'warehouse caller smoke test: FAILED (requests retained for diagnosis)\n' >&2
  exit 1
fi

if [[ "$keep_requests" == 0 ]]; then
  kubectl -n "$namespace" delete scanrequest "$scan_request" >/dev/null
  if [[ "$caller" == asset ]]; then
    kubectl -n "$namespace" delete mocdiscoveryrequest "$moc_request" >/dev/null
  fi
  printf 'warehouse caller smoke test: PASSED (successful request resources cleaned)\n'
else
  printf 'warehouse caller smoke test: PASSED (requests retained: %s)\n' "${requests[*]}"
fi
