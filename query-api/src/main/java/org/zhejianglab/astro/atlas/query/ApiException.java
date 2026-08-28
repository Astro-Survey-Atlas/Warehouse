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

package org.zhejianglab.astro.atlas.query;

public final class ApiException extends RuntimeException {
  private final int status;
  private final String code;
  private final String field;

  public ApiException(int status, String code, String message, String field) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String field() {
    return field;
  }

  public static ApiException invalid(String message, String field) {
    return new ApiException(400, "INVALID_QUERY", message, field);
  }
}
