package org.zhejianglab.astro.atlas.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.KeyToPathBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.zhejianglab.astro.atlas.core.CredentialRef;
import org.zhejianglab.astro.atlas.core.ScanPlan;

/** Renders a secret-free plan and projects its credential references into a scanner Pod. */
public final class PlanMaterializer {
  private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final String CREDENTIAL_ROOT = "/etc/atlas/credentials";
  private final ObjectMapper mapper;

  public PlanMaterializer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public RenderedPlan render(ScanPlan plan, CredentialsSpec credentials) {
    if (credentials == null) credentials = CredentialsSpec.empty();
    ObjectNode root = mapper.valueToTree(plan);
    List<Volume> volumes = new ArrayList<>();
    List<VolumeMount> mounts = new ArrayList<>();
    Map<String, EnvVar> environment = new LinkedHashMap<>();
    materializeConnector(root.with("source").with("connector"), "source",
        plan.source().connector().credentialRef(), credentials.source(), environment, volumes, mounts);
    materializeConnector(root.with("sink").with("connector"), "sink",
        plan.sink().connector().credentialRef(), credentials.sink(), environment, volumes, mounts);
    try {
      String json = mapper.writeValueAsString(root);
      String bindingFingerprint = bindingFingerprint(credentials);
      return new RenderedPlan(json, sha256(json + "\n" + bindingFingerprint),
          List.copyOf(environment.values()), volumes, mounts);
    } catch (Exception exception) {
      throw new IllegalStateException("cannot serialize scan plan", exception);
    }
  }

  private void materializeConnector(
      ObjectNode connector,
      String scope,
      CredentialRef ref,
      SecretBinding binding,
      Map<String, EnvVar> environment,
      List<Volume> volumes,
      List<VolumeMount> mounts) {
    if (ref == null) return;
    validatePairs(scope, ref);
    List<KeyToPathBuilder> fileItems = new ArrayList<>();
    addEnvironment(environment, ref.accessKeyEnv(), "accessKey", binding);
    addEnvironment(environment, ref.secretKeyEnv(), "secretKey", binding);
    addEnvironment(environment, ref.usernameEnv(), "username", binding);
    addEnvironment(environment, ref.passwordEnv(), "password", binding);
    addFile(connector, scope, fileItems, "accessKeyFile", ref.accessKeyFile(), binding, "accessKey");
    addFile(connector, scope, fileItems, "secretKeyFile", ref.secretKeyFile(), binding, "secretKey");
    addFile(connector, scope, fileItems, "usernameFile", ref.usernameFile(), binding, "username");
    addFile(connector, scope, fileItems, "passwordFile", ref.passwordFile(), binding, "password");
    if (!fileItems.isEmpty()) {
      String volumeName = scope + "-credentials";
      String mountPath = CREDENTIAL_ROOT + "/" + scope;
      volumes.add(new VolumeBuilder().withName(volumeName)
          .withSecret(new SecretVolumeSourceBuilder().withSecretName(binding.secretName())
              .withItems(fileItems.stream().map(KeyToPathBuilder::build).toList()).build())
          .build());
      mounts.add(new VolumeMountBuilder().withName(volumeName).withMountPath(mountPath)
          .withReadOnly(true).build());
    }
  }

  private void addEnvironment(Map<String, EnvVar> environment, String name, String part, SecretBinding binding) {
    if (name == null || name.isBlank()) return;
    requireBinding(binding, part);
    if (!ENVIRONMENT_NAME.matcher(name).matches()) {
      throw new OperatorValidationException(List.of("credential environment name is invalid: " + name));
    }
    EnvVar value = new io.fabric8.kubernetes.api.model.EnvVarBuilder().withName(name)
        .withValueFrom(new EnvVarSourceBuilder().withNewSecretKeyRef(binding.key(part), binding.secretName(), false).build())
        .build();
    EnvVar previous = environment.putIfAbsent(name, value);
    if (previous != null && !previous.equals(value)) {
      throw new OperatorValidationException(List.of("credential environment name is bound more than once: " + name));
    }
  }

  private void addFile(
      ObjectNode connector,
      String scope,
      List<KeyToPathBuilder> items,
      String field,
      String originalPath,
      SecretBinding binding,
      String part) {
    if (originalPath == null || originalPath.isBlank()) return;
    requireBinding(binding, part);
    String path = CREDENTIAL_ROOT + "/" + scope + "/" + part;
    connector.with("credentialRef").put(field, path);
    items.add(new KeyToPathBuilder().withKey(binding.key(part)).withPath(part));
  }

  private static void requireBinding(SecretBinding binding, String part) {
    if (binding == null || !binding.configured() || binding.key(part) == null || binding.key(part).isBlank()) {
      throw new OperatorValidationException(List.of("credentials binding is required for " + part));
    }
  }

  private static void validatePairs(String scope, CredentialRef ref) {
    if (configured(ref.accessKeyEnv(), ref.accessKeyFile())
        != configured(ref.secretKeyEnv(), ref.secretKeyFile())) {
      throw new OperatorValidationException(List.of(
          scope + " access key and secret key references must be configured together"));
    }
    if (configured(ref.usernameEnv(), ref.usernameFile())
        != configured(ref.passwordEnv(), ref.passwordFile())) {
      throw new OperatorValidationException(List.of(
          scope + " username and password references must be configured together"));
    }
  }

  private static boolean configured(String environment, String file) {
    return (environment != null && !environment.isBlank()) || (file != null && !file.isBlank());
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte item : digest) result.append(String.format("%02x", item));
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String bindingFingerprint(CredentialsSpec credentials) {
    return bindingFingerprint("source", credentials.source()) + "\n"
        + bindingFingerprint("sink", credentials.sink());
  }

  private static String bindingFingerprint(String scope, SecretBinding binding) {
    if (binding == null) return scope + "=none";
    return scope + "=" + String.join("|", value(binding.secretName()), value(binding.accessKeyKey()),
        value(binding.secretKeyKey()), value(binding.usernameKey()), value(binding.passwordKey()));
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
