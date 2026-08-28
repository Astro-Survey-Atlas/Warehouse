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
