package org.zhejianglab.astro.atlas.core;

public record SinkConnector(SinkType type, String endpoint, CredentialRef credentialRef) {
  public SinkConnector {
    if (credentialRef == null) credentialRef = CredentialRef.none();
  }
}
