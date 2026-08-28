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

package org.zhejianglab.astro.atlas.scanner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.SourceType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3SourceAdapterTest {
  @Test
  void requestsOnlyTheFitsHeaderRange() throws Exception {
    byte[] header = "SIMPLE  =                    T".getBytes(StandardCharsets.US_ASCII);
    AtomicReference<String> range = new AtomicReference<>();
    HttpServer server = server(exchange -> {
      range.set(exchange.getRequestHeaders().getFirst("Range"));
      exchange.getResponseHeaders().set("Content-Range", "bytes 0-" + (header.length - 1) + "/1000000");
      send(exchange, 206, header);
    });
    server.start();
    try (S3SourceAdapter adapter = adapter(server)) {
      assertArrayEquals(header, adapter.open(item()).open().readAllBytes());
    } finally {
      server.stop(0);
    }
    assertEquals("bytes=0-737279", range.get());
  }

  @Test
  void fallsBackToAFullGetWhenTheServerIgnoresRange() throws Exception {
    byte[] object = "full-object-after-range-fallback".getBytes(StandardCharsets.US_ASCII);
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = server(exchange -> {
      requests.incrementAndGet();
      send(exchange, 200, object);
    });
    server.start();
    try (S3SourceAdapter adapter = adapter(server)) {
      assertArrayEquals(object, adapter.open(item()).open().readAllBytes());
    } finally {
      server.stop(0);
    }
    assertEquals(2, requests.get());
  }

  @Test
  void doesNotHideUnexpectedObjectStoreErrors() throws Exception {
    HttpServer server = server(exchange -> send(exchange, 500,
        "<Error><Code>InternalError</Code><Message>backend failed</Message></Error>"
            .getBytes(StandardCharsets.UTF_8)));
    server.start();
    try (S3SourceAdapter adapter = adapter(server)) {
      assertThrows(S3Exception.class, () -> adapter.open(item()).open());
    } finally {
      server.stop(0);
    }
  }

  private static S3SourceAdapter adapter(HttpServer server) {
    S3Client client = S3Client.builder()
        .endpointOverride(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("access", "secret")))
        .build();
    return new S3SourceAdapter(client, SourceType.S3);
  }

  private static InputItem item() {
    return new InputItem("s3://bucket/image.fits", "image.fits", "s3://bucket/", FileType.FITS, 1_000_000L, null);
  }

  private static HttpServer server(java.util.function.Consumer<HttpExchange> handler) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> handler.accept(exchange));
    return server;
  }

  private static void respond(HttpExchange exchange, int status, byte[] body) throws java.io.IOException {
    exchange.sendResponseHeaders(status, body.length);
    try (var output = exchange.getResponseBody()) {
      output.write(body);
    }
  }

  private static void send(HttpExchange exchange, int status, byte[] body) {
    try {
      respond(exchange, status, body);
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }
}
