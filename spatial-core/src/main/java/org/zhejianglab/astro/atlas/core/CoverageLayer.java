package org.zhejianglab.astro.atlas.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CoverageLayer(
    String layerId,
    String surveyId,
    String releaseId,
    String productId,
    Modality modality,
    CoverageRole coverageRole,
    String entrypoint,
    LayerState state,
    String scanRunId,
    Instant leaseExpiresAt,
    String sourceSnapshotSha256,
    List<Integer> availableOrders,
    long fileCount,
    long coverageCount,
    int errorCount,
    String errorSummary,
    Instant updatedAt) {
  public CoverageLayer {
    new LayerSpec(layerId, surveyId, releaseId, productId, modality, coverageRole, entrypoint);
    if (state == null) throw new IllegalArgumentException("layer state is required");
    if (scanRunId == null || scanRunId.isBlank()) throw new IllegalArgumentException("scanRunId is required");
    if (state == LayerState.UPDATING && leaseExpiresAt == null) throw new IllegalArgumentException("updating layer lease is required");
    if (state != LayerState.UPDATING && leaseExpiresAt != null) throw new IllegalArgumentException("only updating layers may hold a lease");
    if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
    if (fileCount < 0 || coverageCount < 0 || errorCount < 0) throw new IllegalArgumentException("layer counts must not be negative");
    availableOrders = availableOrders == null ? List.of() : availableOrders.stream().distinct().sorted().toList();
    availableOrders.forEach(Healpix::validateOrder);
  }

  public static CoverageLayer updating(LayerSpec layer, String scanRunId, Instant leaseExpiresAt) {
    return from(layer, LayerState.UPDATING, scanRunId, leaseExpiresAt, null, List.of(), 0, 0, 0, null);
  }

  public CoverageLayer active(String snapshotSha256, List<Integer> orders, long files, long coverages, int errors) {
    return from(spec(), LayerState.ACTIVE, scanRunId, null, snapshotSha256, orders, files, coverages, errors, null);
  }

  public CoverageLayer failed(String summary, String snapshotSha256, int errors) {
    return from(spec(), LayerState.FAILED, scanRunId, null, snapshotSha256, List.of(), 0, 0, errors, summary);
  }

  public LayerSpec spec() {
    return new LayerSpec(layerId, surveyId, releaseId, productId, modality, coverageRole, entrypoint);
  }

  public Map<String, Object> toDocument() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("layer_id", layerId);
    document.put("survey_id", surveyId);
    document.put("release_id", releaseId);
    document.put("product_id", productId);
    document.put("modality", modality.value());
    document.put("coverage_role", coverageRole.value());
    document.put("entrypoint", entrypoint);
    document.put("state", state.value());
    document.put("scan_run_id", scanRunId);
    document.put("lease_expires_at", leaseExpiresAt);
    document.put("source_snapshot_sha256", sourceSnapshotSha256);
    document.put("available_orders", availableOrders);
    document.put("file_count", fileCount);
    document.put("coverage_count", coverageCount);
    document.put("error_count", errorCount);
    document.put("error_summary", errorSummary);
    document.put("updated_at", updatedAt);
    return Collections.unmodifiableMap(document);
  }

  private static CoverageLayer from(LayerSpec layer, LayerState state, String runId, Instant lease,
      String snapshot, List<Integer> orders, long files, long coverages, int errors, String summary) {
    return new CoverageLayer(layer.layerId(), layer.surveyId(), layer.releaseId(), layer.productId(),
        layer.modality(), layer.coverageRole(), layer.entrypoint(), state, runId, lease, snapshot,
        orders, files, coverages, errors, summary, Instant.now());
  }
}
