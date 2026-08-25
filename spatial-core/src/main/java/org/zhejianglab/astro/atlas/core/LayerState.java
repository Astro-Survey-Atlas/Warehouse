package org.zhejianglab.astro.atlas.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum LayerState {
  UPDATING,
  ACTIVE,
  FAILED;

  @JsonValue
  public String value() {
    return name();
  }

  @JsonCreator
  public static LayerState fromJson(String value) {
    return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
