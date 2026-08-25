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
