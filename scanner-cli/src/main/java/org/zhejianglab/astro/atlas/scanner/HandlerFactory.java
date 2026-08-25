package org.zhejianglab.astro.atlas.scanner;

import java.util.Locale;

public final class HandlerFactory {
  private HandlerFactory() {}

  public static Handler create(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "default" -> new DefaultHandler();
      case "fits" -> new FitsHeaderHandler();
      case "catalog" -> new CatalogHandler();
      case "coverage" -> new CoverageHandler();
      default -> throw new IllegalArgumentException("unknown handler: " + name);
    };
  }
}
