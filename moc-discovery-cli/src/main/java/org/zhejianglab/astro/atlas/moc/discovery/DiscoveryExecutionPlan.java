package org.zhejianglab.astro.atlas.moc.discovery;

import java.net.URI;
import java.util.List;

/** Immutable, operator-generated execution details; never entered in the Admin form. */
public record DiscoveryExecutionPlan(DiscoveryIntent intent, DiscoveryPolicy policy, URI searchUri, List<URI> probeUris) {
  public DiscoveryExecutionPlan {
    if (!policy.id().equals(intent.policyRef())) throw new IllegalArgumentException("unsupported discovery policy: " + intent.policyRef());
    if (!policy.allows(searchUri)) throw new IllegalArgumentException("search URI is not allowlisted");
    probeUris = List.copyOf(probeUris);
    if (probeUris.size() > policy.maxProbes()) throw new IllegalArgumentException("probe count exceeds policy");
    probeUris.forEach(uri -> { if (!policy.allows(uri)) throw new IllegalArgumentException("probe URI is not allowlisted"); });
  }
}
