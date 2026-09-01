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
  /** The public status projection is deliberately smaller than the search cap. */
  public static final int STATUS_CANDIDATE_LIMIT = 50;

  public static DiscoveryPolicy cdsPublicMocV2() {
    // MAXREC=51 lets the worker distinguish exactly 50 records from a result
    // that has at least one more record. The 51st record is evidence only and
    // never enters the Kubernetes status projection.
    return new DiscoveryPolicy("cds-public-moc-v2", List.of("alasky.cds.unistra.fr", "alasky.unistra.fr", "cds.unistra.fr"), 51, 0, 1,
        64L * 1024 * 1024, 256L * 1024 * 1024, Duration.ofSeconds(20), Duration.ofMinutes(10), 12);
  }

  public int statusCandidateLimit() {
    return Math.min(STATUS_CANDIDATE_LIMIT, maxCandidates);
  }

  public boolean allows(URI uri) {
    if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return false;
    String host = uri.getHost();
    return host != null && allowedHosts.stream().anyMatch(allowed -> host.equalsIgnoreCase(allowed) || host.toLowerCase().endsWith("." + allowed));
  }
}
