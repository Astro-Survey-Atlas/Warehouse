package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum SourceType {
  LOCAL("local"),
  S3("s3"),
  OSS("oss");

  private final String value;

  SourceType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static SourceType fromValue(String value) {
    for (SourceType type : values()) {
      if (type.value.equals(value == null ? "" : value.toLowerCase(Locale.ROOT))) return type;
    }
    throw new IllegalArgumentException("unsupported source connector type: " + value);
  }

  @JsonValue
  public String value() {
    return value;
  }
}
