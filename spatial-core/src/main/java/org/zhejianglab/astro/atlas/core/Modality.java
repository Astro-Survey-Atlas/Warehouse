package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Optional descriptive metadata; it is never inferred by the core model. */
public record Modality(String value) {
  public Modality {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("modality must not be blank");
    value = value.trim();
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static Modality fromJson(String value) {
    return new Modality(value);
  }

  @JsonValue
  public String value() {
    return value;
  }

  public static Modality of(String value) {
    return value == null || value.isBlank() ? null : new Modality(value);
  }
}
