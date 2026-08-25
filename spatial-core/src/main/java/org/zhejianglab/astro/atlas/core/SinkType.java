package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum SinkType {
  ELASTICSEARCH("elasticsearch");

  private final String value;

  SinkType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static SinkType fromValue(String value) {
    for (SinkType type : values()) {
      if (type.value.equals(value == null ? "" : value.toLowerCase(Locale.ROOT))) return type;
    }
    throw new IllegalArgumentException("unsupported sink connector type: " + value);
  }

  @JsonValue
  public String value() {
    return value;
  }
}
