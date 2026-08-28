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
