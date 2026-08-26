package org.zhejianglab.astro.atlas.core;

import java.util.Locale;

public enum FileType {
  FITS,
  CSV,
  TSV,
  CATALOG,
  UNKNOWN;

  public static FileType fromFileName(String fileName) {
    String value = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    if (value.endsWith(".fits") || value.endsWith(".fit")) return FITS;
    if (value.endsWith(".csv")) return CSV;
    if (value.endsWith(".tsv")) return TSV;
    if (value.endsWith(".cat")) return CATALOG;
    return UNKNOWN;
  }
}
