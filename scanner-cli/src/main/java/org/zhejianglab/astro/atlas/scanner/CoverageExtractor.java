package org.zhejianglab.astro.atlas.scanner;

import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceContent;

public interface CoverageExtractor {
  ExtractionResult extract(InputItem item, SourceContent content, ScanPlan plan);
}
