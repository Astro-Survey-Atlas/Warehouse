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

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryPlanTest {
  @Test void buildsIntentOnlyPlanWithCentralLimits() {
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV1();
    DiscoveryExecutionPlan plan = DiscoveryPlanBuilder.build(new DiscoveryIntent("Gaia", "DR3", "main source", policy.id()), policy);
    assertEquals("cds-public-moc-v1", plan.policy().id());
    assertTrue(plan.searchUri().toString().contains("gaia"));
    assertTrue(policy.allows(plan.searchUri()));
  }

  @Test void rejectsNonAllowlistedUrl() {
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV1();
    assertFalse(policy.allows(java.net.URI.create("https://example.org/moc.fits")));
    assertThrows(IllegalArgumentException.class, () -> new DiscoveryExecutionPlan(new DiscoveryIntent("Gaia", null, null, policy.id()), policy, java.net.URI.create("https://example.org"), java.util.List.of()));
  }

  @Test void workerParsesSearchBodyAndKeepsRequestEvidenceBounded() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/search", exchange -> {
      byte[] body = ("[{\"id\":\"gaia-dr3\",\"moc_url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/moc\"}]").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
    });
    server.createContext("/moc", exchange -> {
      byte[] body = "MOC ICRS NESTED ORDER 8".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
    });
    server.start();
    try {
      DiscoveryPolicy policy = new DiscoveryPolicy("test", List.of("127.0.0.1"), 5, 5, 5, 1024 * 1024, 2 * 1024 * 1024, Duration.ofSeconds(2), Duration.ofSeconds(10), 12);
      DiscoveryIntent intent = new DiscoveryIntent("Gaia", null, null, policy.id());
      DiscoveryExecutionPlan plan = new DiscoveryExecutionPlan(intent, policy, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"), List.of());
      Map<String, Object> evidence = new MocDiscoveryWorker(policy).run(plan);
      assertEquals(1, evidence.get("candidateCount"));
      assertEquals(1, evidence.get("probeCount"));
      assertTrue(((List<?>) evidence.get("probes")).stream().anyMatch(value -> value instanceof Map<?, ?> map
          && map.get("validation") instanceof Map<?, ?> validation
          && Boolean.TRUE.equals(validation.get("acceptedSpatialMoc"))));
      assertTrue(((List<?>) evidence.get("requests")).stream().noneMatch(value -> value instanceof Map<?, ?> map && map.containsKey("body")));
    } finally {
      server.stop(0);
    }
  }
}
