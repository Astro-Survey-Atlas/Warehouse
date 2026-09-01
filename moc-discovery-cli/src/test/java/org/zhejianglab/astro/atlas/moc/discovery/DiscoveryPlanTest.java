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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryPlanTest {
  @Test void buildsMocServerFilterQueryInsteadOfUnsupportedAdql() {
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV2();
    DiscoveryExecutionPlan plan = DiscoveryPlanBuilder.build(new DiscoveryIntent("JWST", null, null, policy.id()), policy);

    String query = decodedQuery(plan.searchUri());
    assertTrue(query.contains("obs_collection=*jwst*"));
    assertTrue(query.contains("get=record"));
    assertTrue(query.contains("fmt=json"));
    assertTrue(query.contains("MAXREC=51"));
    assertFalse(query.contains("queryData"));
    assertFalse(query.contains("ivoa.ObsCore"));
  }

  @Test void includesReleaseAndProductHintsInTheCollectionFilter() {
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV2();
    DiscoveryExecutionPlan plan = DiscoveryPlanBuilder.build(new DiscoveryIntent("Gaia", "DR3", "main source", policy.id()), policy);

    String query = decodedQuery(plan.searchUri());
    assertTrue(query.contains("obs_collection=*gaia*"));
    assertTrue(query.contains("obs_title=*dr3*"));
    assertTrue(query.contains("obs_id=*main source*"));
    assertTrue(query.contains("hips_service_url=*"));
    assertTrue(query.contains("moc_access_url=*"));
  }

  @Test void buildsIntentOnlyPlanWithCentralLimits() {
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV2();
    DiscoveryExecutionPlan plan = DiscoveryPlanBuilder.build(new DiscoveryIntent("Gaia", "DR3", "main source", policy.id()), policy);
    assertEquals("cds-public-moc-v2", plan.policy().id());
    assertTrue(plan.searchUri().toString().contains("gaia"));
    assertTrue(policy.allows(plan.searchUri()));
  }

  private static String decodedQuery(URI uri) {
    return java.util.Arrays.stream(uri.getRawQuery().split("&"))
        .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  @Test void rejectsNonAllowlistedUrl() {
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV2();
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
      DiscoveryPolicy policy = new DiscoveryPolicy("test", List.of("127.0.0.1"), 51, 0, 1, 1024 * 1024, 2 * 1024 * 1024, Duration.ofSeconds(2), Duration.ofSeconds(10), 12);
      DiscoveryIntent intent = new DiscoveryIntent("Gaia", null, null, policy.id());
      DiscoveryExecutionPlan plan = new DiscoveryExecutionPlan(intent, policy, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"), List.of());
      Path evidenceRoot = Files.createTempDirectory("atlas-moc-discovery-");
      Map<String, Object> evidence = new MocDiscoveryWorker(policy).run(plan, evidenceRoot);
      assertEquals(1, evidence.get("candidateCount"));
      assertEquals(0, evidence.get("probeCount"));
      Map<?, ?> searchRequest = (Map<?, ?>) ((List<?>) evidence.get("requests")).get(0);
      assertTrue(String.valueOf(searchRequest.get("sha256")).matches("[a-f0-9]{64}"));
      Path retainedSearch = evidenceRoot.resolve(String.valueOf(searchRequest.get("evidenceRef")));
      assertTrue(Files.isRegularFile(retainedSearch));
      assertTrue(Files.readString(retainedSearch).contains("gaia-dr3"));
      assertTrue(((List<?>) evidence.get("probes")).isEmpty());
      assertTrue(((List<?>) evidence.get("requests")).stream().noneMatch(value -> value instanceof Map<?, ?> map && map.containsKey("body")));
    } finally {
      server.stop(0);
    }
  }

  @Test void workerParsesCdsRecordFieldsAndAcceptsCelestialCodedMoc() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/search", exchange -> {
      byte[] body = ("[{\"ID\":\"CDS/P/JWST/Test\",\"obs_collection\":\"JWST\",\"obs_title\":\"JWST test\",\"moc_access_url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/moc\"}]").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
    });
    server.createContext("/moc", exchange -> {
      byte[] body = "SIMPLE  = T COORDSYS= 'C' ORDERING= 'NUNIQ' MOCDIM= 'SPACE' MOCORDER= 8".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
    });
    server.start();
    try {
      DiscoveryPolicy policy = new DiscoveryPolicy("test", List.of("127.0.0.1"), 51, 0, 1, 1024 * 1024, 2 * 1024 * 1024, Duration.ofSeconds(2), Duration.ofSeconds(10), 12);
      DiscoveryIntent intent = new DiscoveryIntent("JWST", null, null, policy.id());
      DiscoveryExecutionPlan plan = new DiscoveryExecutionPlan(intent, policy, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"), List.of());
      Map<String, Object> evidence = new MocDiscoveryWorker(policy).run(plan);
      assertEquals(1, evidence.get("candidateCount"));
      assertEquals(0, evidence.get("probeCount"));
      Map<?, ?> candidate = (Map<?, ?>) ((List<?>) evidence.get("candidates")).get(0);
      assertEquals("CDS/P/JWST/Test", candidate.get("candidateId"));
      assertEquals("http://127.0.0.1:" + server.getAddress().getPort() + "/moc", candidate.get("mocUrl"));
      assertTrue(((List<?>) evidence.get("probes")).isEmpty());
    } finally {
      server.stop(0);
    }
  }

  @Test void workerDistinguishesProtocolFailuresFromAValidEmptyRecordSet() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/empty", exchange -> { exchange.sendResponseHeaders(200, 0); exchange.close(); });
    server.createContext("/invalid", exchange -> {
      byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
    });
    server.createContext("/valid-empty", exchange -> {
      byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
    });
    server.start();
    try {
      DiscoveryPolicy policy = new DiscoveryPolicy("test", List.of("127.0.0.1"), 51, 0, 1, 1024 * 1024, 2 * 1024 * 1024, Duration.ofSeconds(2), Duration.ofSeconds(10), 12);
      DiscoveryIntent intent = new DiscoveryIntent("JWST", null, null, policy.id());
      for (String path : List.of("/empty", "/invalid", "/valid-empty")) {
        DiscoveryExecutionPlan plan = new DiscoveryExecutionPlan(intent, policy, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path), List.of());
        Map<String, Object> evidence = new MocDiscoveryWorker(policy).run(plan);
        Map<?, ?> request = (Map<?, ?>) ((List<?>) evidence.get("requests")).get(0);
        assertEquals(0, evidence.get("candidateCount"));
        assertEquals(0, evidence.get("probeCount"));
        if (path.equals("/valid-empty")) {
          assertTrue(Boolean.TRUE.equals(request.get("ok")));
          assertFalse(request.containsKey("error"));
        } else {
          assertFalse(Boolean.TRUE.equals(request.get("ok")));
          assertEquals(path.equals("/empty") ? "empty-response" : "invalid-json-response", request.get("error"));
        }
      }
    } finally {
      server.stop(0);
    }
  }
}
