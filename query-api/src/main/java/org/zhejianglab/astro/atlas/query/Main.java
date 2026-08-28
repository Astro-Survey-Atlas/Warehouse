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

import org.zhejianglab.astro.atlas.es.ElasticsearchAdapter;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
    String endpoint = args.length > 1 ? args[1] : System.getenv().getOrDefault("ES_ENDPOINT", "http://localhost:9200");
    String username = System.getenv("ES_USERNAME");
    String password = System.getenv("ES_PASSWORD");
    try (ElasticsearchAdapter index = new ElasticsearchAdapter(endpoint, username, password);
         QueryHttpServer server = new QueryHttpServer("0.0.0.0", port, new QueryService(index))) {
      server.start();
      System.out.println("query-api listening on http://0.0.0.0:" + server.port());
      Thread.currentThread().join();
    }
  }
}
