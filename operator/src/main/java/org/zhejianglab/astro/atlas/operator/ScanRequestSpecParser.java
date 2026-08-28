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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.zhejianglab.astro.atlas.core.PlanValidationException;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.ScanPlanValidator;

public final class ScanRequestSpecParser {
  private final ObjectMapper mapper;

  public ScanRequestSpecParser() {
    mapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
  }

  public ParsedScanRequest parse(GenericKubernetesResource resource, String defaultScannerImage) {
    List<String> errors = new ArrayList<>();
    if (!OperatorConstants.API_VERSION.equals(resource.getApiVersion())) {
      errors.add("apiVersion must be " + OperatorConstants.API_VERSION);
    }
    if (!OperatorConstants.KIND.equals(resource.getKind())) {
      errors.add("kind must be " + OperatorConstants.KIND);
    }
    if (resource.getMetadata() == null || resource.getMetadata().getName() == null
        || resource.getMetadata().getName().isBlank()) {
      errors.add("metadata.name is required");
    }
    JsonNode specNode = mapper.valueToTree(resource.get("spec"));
    if (specNode == null || specNode.isNull() || !specNode.isObject()) {
      errors.add("spec is required");
      throw new OperatorValidationException(errors);
    }
    ScanRequestSpec spec = null;
    try {
      spec = mapper.treeToValue(specNode, ScanRequestSpec.class);
      ScanPlanValidator.validate(spec.plan());
      validateEvidenceMount(spec, errors);
    } catch (PlanValidationException exception) {
      errors.addAll(exception.errors());
    } catch (Exception exception) {
      errors.add("spec is invalid: " + exception.getMessage());
    }
    if (!errors.isEmpty()) throw new OperatorValidationException(errors);
    String image = spec.scanner().image();
    if (image == null || image.isBlank()) image = defaultScannerImage;
    if (image == null || image.isBlank()) errors.add("scanner.image or SCANNER_IMAGE is required");
    if (!errors.isEmpty()) throw new OperatorValidationException(errors);
    ScannerSpec scanner = new ScannerSpec(image, spec.scanner().serviceAccountName(),
        spec.scanner().backoffLimit(), spec.scanner().activeDeadlineSeconds(),
        spec.scanner().ttlSecondsAfterFinished(), spec.scanner().resources(), spec.scanner().evidence());
    return new ParsedScanRequest(new ScanRequestSpec(spec.plan(), scanner, spec.credentials()),
        resource.getMetadata().getName());
  }

  private static void validateEvidenceMount(ScanRequestSpec spec, List<String> errors) {
    if (spec == null || spec.plan() == null || spec.plan().evidence() == null
        || spec.plan().evidence().outputPath() == null) return;
    EvidenceVolumeSpec volume = spec.scanner() == null ? null : spec.scanner().evidence();
    if (volume == null) {
      errors.add("scanner.evidence is required for persisted evidence.outputPath");
      return;
    }
    if (volume.readOnly()) {
      errors.add("scanner.evidence.readOnly must be false for persisted evidence");
    }
    try {
      Path mount = Path.of(volume.mountPath()).normalize();
      Path output = Path.of(spec.plan().evidence().outputPath()).normalize();
      if (!output.isAbsolute() || !output.startsWith(mount)) {
        errors.add("evidence.outputPath must be under scanner.evidence.mountPath");
      }
    } catch (RuntimeException exception) {
      errors.add("evidence.outputPath must be a valid path under scanner.evidence.mountPath");
    }
  }

  public record ParsedScanRequest(ScanRequestSpec spec, String name) {}
}
