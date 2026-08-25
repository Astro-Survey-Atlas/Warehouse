package org.zhejianglab.astro.atlas.core;

public record SourceConnector(SourceType type, String endpoint, String region, CredentialRef credentialRef) {
  public SourceConnector {
    if (credentialRef == null) credentialRef = CredentialRef.none();
  }

  public SourceConnector(SourceType type, String endpoint, CredentialRef credentialRef) {
    this(type, endpoint, null, credentialRef);
  }
}
