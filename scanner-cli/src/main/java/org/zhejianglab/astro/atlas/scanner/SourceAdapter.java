package org.zhejianglab.astro.atlas.scanner;

import java.util.stream.Stream;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceContent;

/** Lists source items and opens their content without exposing transport details. */
public interface SourceAdapter extends AutoCloseable {
  /**
   * Returns a lazy, closeable enumeration. Implementations must keep only a bounded
   * amount of source-side state while the stream is consumed.
   */
  Stream<InputItem> enumerate(ScanPlan plan);

  SourceContent open(InputItem item);

  @Override
  default void close() {}
}
