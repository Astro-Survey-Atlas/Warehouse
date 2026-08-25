package org.zhejianglab.astro.atlas.es;

import java.util.HashMap;
import java.util.Map;

/** Explicit bootstrap tool for templates and the two fixed product indices. */
public final class IndexAdminMain {
  private IndexAdminMain() {}

  public static void main(String[] args) {
    Map<String, String> options = options(args);
    String endpoint = required(options, "endpoint", System.getenv().getOrDefault("ES_ENDPOINT", "http://localhost:9200"));
    String username = environment(options.get("username-env"));
    String password = environment(options.get("password-env"));
    boolean install = options.containsKey("install");
    boolean recreate = options.containsKey("recreate");
    boolean verify = options.containsKey("verify");
    if (!install && !recreate && !verify) {
      throw new IllegalArgumentException("one of --install, --recreate, or --verify is required");
    }
    try (ElasticsearchAdapter adapter = new ElasticsearchAdapter(endpoint, username, password)) {
      if (install || recreate) adapter.installIndexTemplates();
      if (recreate) adapter.recreateFixedIndices();
      if (verify) adapter.verifyIndexMappings();
    }
    System.out.println("index-admin completed");
  }

  private static Map<String, String> options(String[] args) {
    Map<String, String> options = new HashMap<>();
    for (int index = 0; index < args.length; index++) {
      String argument = args[index];
      if (!argument.startsWith("--")) throw new IllegalArgumentException("unknown argument: " + argument);
      String name = argument.substring(2);
      if (name.equals("install") || name.equals("recreate") || name.equals("verify")) {
        if (options.put(name, "true") != null) throw new IllegalArgumentException("duplicate option: " + argument);
        continue;
      }
      if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
        throw new IllegalArgumentException("option requires a value: " + argument);
      }
      if (options.put(name, args[++index]) != null) throw new IllegalArgumentException("duplicate option: " + argument);
    }
    return options;
  }

  private static String required(Map<String, String> options, String name, String fallback) {
    String value = options.getOrDefault(name, fallback);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("--" + name + " is required");
    return value;
  }

  private static String environment(String name) {
    return name == null || name.isBlank() ? null : System.getenv(name);
  }
}
