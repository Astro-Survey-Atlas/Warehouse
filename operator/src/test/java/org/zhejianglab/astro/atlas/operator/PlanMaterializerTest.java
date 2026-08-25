package org.zhejianglab.astro.atlas.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;
import org.zhejianglab.astro.atlas.core.CredentialRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanMaterializerTest {
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void projectsEnvironmentReferencesWithoutSecretValues() {
    CredentialRef ref = new CredentialRef(null, null, "ATLAS_ES_USER", "ATLAS_ES_PASSWORD", null, null, null, null);
    RenderedPlan rendered = new PlanMaterializer(mapper).render(
        OperatorTestFixtures.localPlan(ref),
        new CredentialsSpec(null, new SecretBinding("es-credentials", null, null, "username", "password")));

    assertTrue(rendered.json().contains("ATLAS_ES_USER"));
    assertFalse(rendered.json().contains("es-credentials"));
    assertEquals(2, rendered.environment().size());
    EnvVar user = rendered.environment().get(0);
    assertEquals("ATLAS_ES_USER", user.getName());
    assertEquals("es-credentials", user.getValueFrom().getSecretKeyRef().getName());
    assertEquals("username", user.getValueFrom().getSecretKeyRef().getKey());
    assertTrue(rendered.volumes().isEmpty());
  }

  @Test
  void rewritesFileReferencesToReadOnlySecretMounts() {
    CredentialRef ref = new CredentialRef(null, null, null, null,
        null, null, "/provided/es-user", "/provided/es-password");
    RenderedPlan rendered = new PlanMaterializer(mapper).render(
        OperatorTestFixtures.localPlan(ref),
        new CredentialsSpec(null, new SecretBinding("es-credentials", null, null, "user", "pass")));

    assertTrue(rendered.json().contains("/etc/atlas/credentials/sink/username"));
    assertTrue(rendered.json().contains("/etc/atlas/credentials/sink/password"));
    assertEquals(1, rendered.volumes().size());
    assertEquals("es-credentials", rendered.volumes().get(0).getSecret().getSecretName());
    assertTrue(rendered.volumeMounts().get(0).getReadOnly());
  }

  @Test
  void rejectsCredentialReferencesWithoutSecretBinding() {
    CredentialRef ref = new CredentialRef(null, null, "ATLAS_ES_USER", "ATLAS_ES_PASSWORD", null, null, null, null);
    assertThrows(OperatorValidationException.class, () -> new PlanMaterializer(mapper).render(
        OperatorTestFixtures.localPlan(ref), CredentialsSpec.empty()));
  }

  @Test
  void changesIdentityWhenSecretBindingChanges() {
    CredentialRef ref = new CredentialRef(null, null, "ATLAS_ES_USER", "ATLAS_ES_PASSWORD", null, null, null, null);
    PlanMaterializer materializer = new PlanMaterializer(mapper);
    RenderedPlan first = materializer.render(OperatorTestFixtures.localPlan(ref),
        new CredentialsSpec(null, new SecretBinding("es-one", null, null, "username", "password")));
    RenderedPlan second = materializer.render(OperatorTestFixtures.localPlan(ref),
        new CredentialsSpec(null, new SecretBinding("es-two", null, null, "username", "password")));

    assertNotEquals(first.sha256(), second.sha256());
  }
}
