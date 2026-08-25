package org.zhejianglab.astro.atlas.operator;

import java.util.List;

public final class OperatorValidationException extends IllegalArgumentException {
  private final List<String> errors;

  public OperatorValidationException(List<String> errors) {
    super(String.join("; ", errors));
    this.errors = List.copyOf(errors);
  }

  public List<String> errors() {
    return errors;
  }
}
