package org.zhejianglab.astro.atlas.operator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
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
        spec.scanner().ttlSecondsAfterFinished(), spec.scanner().resources());
    return new ParsedScanRequest(new ScanRequestSpec(spec.plan(), scanner, spec.credentials()),
        resource.getMetadata().getName());
  }

  public record ParsedScanRequest(ScanRequestSpec spec, String name) {}
}
