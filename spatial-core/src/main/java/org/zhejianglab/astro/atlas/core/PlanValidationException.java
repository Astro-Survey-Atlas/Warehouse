package org.zhejianglab.astro.atlas.core;

import java.util.List;

public final class PlanValidationException extends IllegalArgumentException {
  private final List<String> errors;

  public PlanValidationException(List<String> errors) {
    super(String.join("; ", errors));
    this.errors = List.copyOf(errors);
  }

  public List<String> errors() {
    return errors;
  }
}
