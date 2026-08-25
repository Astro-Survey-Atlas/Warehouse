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
import org.zhejianglab.astro.atlas.core.SpatialQuery;

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
    sendJson(exchange, service.isReady() ? 200 : 503, Map.of("status", service.isReady() ? "ready" : "not_ready"));
  }

  private void search(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendError(exchange, new ApiException(405, "METHOD_NOT_ALLOWED", "only GET is supported", null));
      return;
    }
    try {
      String path = exchange.getRequestURI().getPath();
      SpatialQuery query = QueryRequestParser.parse(path, exchange.getRequestURI().getRawQuery());
      sendJson(exchange, 200, service.search(query));
    } catch (ApiException exception) {
      sendError(exchange, exception);
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
