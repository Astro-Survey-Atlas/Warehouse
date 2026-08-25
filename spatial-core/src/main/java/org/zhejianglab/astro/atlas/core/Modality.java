package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Modality {
  IMAGE("image"),
  SPECTRUM("spectrum"),
  CUBE("cube"),
  CATALOG("catalog"),
  TIMESERIES("timeseries"),
  VISIBILITY("visibility"),
  EVENT("event"),
  OTHER("other");

  private final String value;

  Modality(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static Modality of(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (Modality modality : values()) {
      if (modality.value.equals(normalized)) return modality;
    }
    throw new IllegalArgumentException("unsupported modality: " + value);
  }
}
