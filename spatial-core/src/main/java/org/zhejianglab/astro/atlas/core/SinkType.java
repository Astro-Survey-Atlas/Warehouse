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
