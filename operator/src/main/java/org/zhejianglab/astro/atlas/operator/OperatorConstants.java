package org.zhejianglab.astro.atlas.operator;

public final class OperatorConstants {
  public static final String API_VERSION = "atlas.zhejianglab.org/v1alpha1";
  public static final String KIND = "ScanRequest";
  public static final String GROUP = "atlas.zhejianglab.org";
  public static final String VERSION = "v1alpha1";
  public static final String PLURAL = "scanrequests";
  public static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
  public static final String OPERATOR_NAME = "astro-atlas-operator";
  public static final String REQUEST_LABEL = "atlas.zhejianglab.org/scan-request";
  public static final String PLAN_HASH_ANNOTATION = "atlas.zhejianglab.org/plan-sha256";
  public static final String PLAN_PATH = "/etc/atlas/scan/plan.json";

  private OperatorConstants() {}
}
