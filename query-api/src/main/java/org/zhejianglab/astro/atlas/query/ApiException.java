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
