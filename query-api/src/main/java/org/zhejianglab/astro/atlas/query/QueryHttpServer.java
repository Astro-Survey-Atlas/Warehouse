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

package org.zhejianglab.astro.atlas.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CoverageLookup;

public final class QueryHttpServer implements AutoCloseable {
  private final HttpServer server;
  private final QueryService service;
  private final ObjectMapper mapper;

  public QueryHttpServer(String host, int port, QueryService service) throws IOException {
    this.service = service;
    this.mapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
    this.server.createContext("/healthz", this::health);
    this.server.createContext("/readyz", this::ready);
    this.server.createContext("/v1/files/", this::search);
    this.server.createContext("/v2/files/", this::search);
  }

  public void start() {
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void health(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendError(exchange, new ApiException(405, "METHOD_NOT_ALLOWED", "only GET is supported", null));
      return;
    }
    sendJson(exchange, 200, Map.of("status", "ok"));
  }

  private void ready(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendError(exchange, new ApiException(405, "METHOD_NOT_ALLOWED", "only GET is supported", null));
      return;
    }
    boolean ready = service.isReady();
    sendJson(exchange, ready ? 200 : 503, Map.of("status", ready ? "ready" : "not_ready"));
  }

  private void search(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendError(exchange, new ApiException(405, "METHOD_NOT_ALLOWED", "only GET is supported", null));
      return;
    }
    try {
      String path = exchange.getRequestURI().getPath();
      if (path.equals("/v2/files/healpix")) {
        CoverageLookup lookup = QueryRequestParser.parseCoverageLookup(path, exchange.getRequestURI().getRawQuery());
        sendJson(exchange, 200, service.search(lookup));
      } else {
        QueryRequestParser.DiagnosticRequest request = QueryRequestParser.parseDiagnostic(path, exchange.getRequestURI().getRawQuery());
        sendJson(exchange, 200, service.search(request.query(), request.layerIds()));
      }
    } catch (ApiException exception) {
      sendError(exchange, exception);
    } catch (LayerStateException exception) {
      sendError(exchange, new ApiException(409, "LAYER_NOT_QUERYABLE", exception.getMessage(), exception.layerId()));
    } catch (LayerOrderException exception) {
      sendError(exchange, new ApiException(409, "ORDER_NOT_AVAILABLE", exception.getMessage(), exception.layerId()));
    } catch (UnknownLayerException exception) {
      sendError(exchange, new ApiException(404, "UNKNOWN_LAYER", exception.getMessage(), exception.layerId()));
    } catch (RuntimeException exception) {
      sendError(exchange, new ApiException(500, "QUERY_FAILED", "query failed", null));
    }
  }

  private void sendError(HttpExchange exchange, ApiException exception) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", exception.code());
    body.put("message", exception.getMessage());
    body.put("field", exception.field());
    sendJson(exchange, exception.status(), body);
  }

  private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
    byte[] body = mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(body);
    }
  }
}
