package org.zhejianglab.astro.atlas.scanner;

import java.util.List;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceContent;

/** Lists source items and opens their content without exposing transport details. */
public interface SourceAdapter {
  List<InputItem> enumerate(ScanPlan plan);

  SourceContent open(InputItem item);
}
