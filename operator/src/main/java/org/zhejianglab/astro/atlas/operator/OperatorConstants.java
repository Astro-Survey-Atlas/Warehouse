/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  public static final String LAYER_LABEL = "atlas.zhejianglab.org/layer";
  public static final String JOB_LABEL = "job-name";
  public static final String TRACKING_LABEL_PREFIX = "atlas.zhejianglab.org/track-";
  public static final String PLAN_HASH_ANNOTATION = "atlas.zhejianglab.org/plan-sha256";
  public static final String EXECUTION_HASH_ANNOTATION = "atlas.zhejianglab.org/execution-sha256";
  public static final String SCANNER_SOURCE_LABEL = "atlas.zhejianglab.org/scanner-source";
  public static final String SCANNER_SOURCE_LABEL_VALUE = "true";
  public static final String PLAN_PATH = "/etc/atlas/scan/plan.json";

  private OperatorConstants() {}
}
