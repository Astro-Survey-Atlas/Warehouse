package org.zhejianglab.astro.atlas.operator;

import org.zhejianglab.astro.atlas.core.ScanPlan;

public record ScanRequestSpec(ScanPlan plan, ScannerSpec scanner, CredentialsSpec credentials) {
  public ScanRequestSpec {
    if (credentials == null) credentials = CredentialsSpec.empty();
    if (scanner == null) scanner = ScannerSpec.defaults("");
  }
}
