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

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.zhejianglab.astro.atlas.core.CatalogSpec;
import org.zhejianglab.astro.atlas.core.CredentialRef;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.ExtractionMode;
import org.zhejianglab.astro.atlas.core.ExtractionSpec;
import org.zhejianglab.astro.atlas.core.EvidenceSpec;
import org.zhejianglab.astro.atlas.core.Filters;
import org.zhejianglab.astro.atlas.core.LayerSpec;
import org.zhejianglab.astro.atlas.core.Modality;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SinkConnector;
import org.zhejianglab.astro.atlas.core.SinkSpec;
import org.zhejianglab.astro.atlas.core.SinkType;
import org.zhejianglab.astro.atlas.core.SourceConnector;
import org.zhejianglab.astro.atlas.core.SourceLocation;
import org.zhejianglab.astro.atlas.core.SourceSpec;
import org.zhejianglab.astro.atlas.core.SourceType;

final class OperatorTestFixtures {
  private OperatorTestFixtures() {}

  static ScanPlan localPlan(CredentialRef sinkCredentials) {
    return new ScanPlan(2, "local-run-20260825",
        new LayerSpec("local-layer", "test-survey", "local", "image", Modality.IMAGE, CoverageRole.FOOTPRINT, null),
        new SourceSpec(new SourceConnector(SourceType.LOCAL, null, null, CredentialRef.none()),
            SourceLocation.local("/survey")),
        Filters.empty(), new ExtractionSpec(ExtractionMode.FITS_HEADER_POSITION, 8, CatalogSpec.empty()),
        new SinkSpec(new SinkConnector(SinkType.ELASTICSEARCH, "http://elasticsearch:9200", sinkCredentials)),
        new EvidenceSpec("/var/lib/atlas-evidence/local-run-20260825"));
  }

  static EvidenceVolumeSpec evidenceVolume() {
    return new EvidenceVolumeSpec("atlas-evidence", "/var/lib/atlas-evidence", false);
  }

  static GenericKubernetesResource request(String name) {
    GenericKubernetesResource resource = new GenericKubernetesResource();
    resource.setApiVersion(OperatorConstants.API_VERSION);
    resource.setKind(OperatorConstants.KIND);
    ObjectMeta metadata = new ObjectMeta();
    metadata.setName(name);
    metadata.setNamespace("atlas");
    metadata.setUid("uid-1");
    metadata.setGeneration(2L);
    resource.setMetadata(metadata);
    return resource;
  }
}
