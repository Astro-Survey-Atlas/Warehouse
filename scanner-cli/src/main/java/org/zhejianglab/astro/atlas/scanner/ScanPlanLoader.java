package org.zhejianglab.astro.atlas.scanner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Path;
import org.zhejianglab.astro.atlas.core.ScanPlan;

public final class ScanPlanLoader {
  private final ObjectMapper mapper;

  public ScanPlanLoader() {
    mapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
  }

  public ScanPlan load(Path path) throws IOException {
    return mapper.readValue(path.toFile(), ScanPlan.class);
  }
}
