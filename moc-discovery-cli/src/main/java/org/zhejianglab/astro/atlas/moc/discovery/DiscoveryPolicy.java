package org.zhejianglab.astro.atlas.moc.discovery;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Central execution limits for public CDS MOC discovery. */
public record DiscoveryPolicy(
    String id,
    List<String> allowedHosts,
    int maxCandidates,
    int maxProbes,
    int maxRequests,
    long maxObjectBytes,
    long maxTaskBytes,
    Duration requestTimeout,
    Duration taskTimeout,
    int maxOrder) {
  public static DiscoveryPolicy cdsPublicMocV1() {
    return new DiscoveryPolicy("cds-public-moc-v1", List.of("alasky.cds.unistra.fr", "cds.unistra.fr"), 50, 10, 40,
        64L * 1024 * 1024, 256L * 1024 * 1024, Duration.ofSeconds(20), Duration.ofMinutes(10), 12);
  }

  public boolean allows(URI uri) {
    if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return false;
    String host = uri.getHost();
    return host != null && allowedHosts.stream().anyMatch(allowed -> host.equalsIgnoreCase(allowed) || host.toLowerCase().endsWith("." + allowed));
  }
}
