package org.zhejianglab.astro.atlas.scanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.IndexWriter;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.QueryLimits;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

/** Deterministic fake index used by local contract tests and development. */
public final class InMemoryIndex implements IndexWriter, IndexReader {
  private final Map<String, FileAsset> files = new ConcurrentHashMap<>();
  private final Map<String, SpatialCoverage> coverages = new ConcurrentHashMap<>();

  @Override
  public void upsertFileAsset(FileAsset fileAsset) {
    files.put(fileAsset.fileId(), fileAsset);
  }

  @Override
  public void upsertCoverage(SpatialCoverage coverage) {
    coverages.put(coverage.id(), coverage);
  }

  @Override
  public Page<SpatialCoverage> searchCoverage(Collection<Long> order8Cells, int limit, String cursor) {
    QueryLimits.validate(limit, cursor);
    Set<Long> requested = Set.copyOf(order8Cells);
    List<SpatialCoverage> matching = coverages.values().stream()
        .filter(coverage -> requested.contains(coverage.healpixCell()))
        .sorted(Comparator.comparing(SpatialCoverage::id))
        .toList();
    int offset = parseCursor(cursor);
    if (offset > matching.size()) throw new IllegalArgumentException("cursor is outside result set");
    int end = Math.min(matching.size(), offset + limit);
    String next = end < matching.size() ? Integer.toString(end) : null;
    return new Page<>(matching.subList(offset, end), next);
  }

  @Override
  public Collection<FileAsset> findFiles(Collection<String> fileIds) {
    List<FileAsset> result = new ArrayList<>();
    for (String fileId : fileIds) {
      FileAsset file = files.get(fileId);
      if (file != null) result.add(file);
    }
    return result;
  }

  public List<FileAsset> files() {
    return files.values().stream().sorted(Comparator.comparing(FileAsset::fileId)).toList();
  }

  public List<SpatialCoverage> coverages() {
    return coverages.values().stream().sorted(Comparator.comparing(SpatialCoverage::id)).toList();
  }

  private static int parseCursor(String cursor) {
    if (cursor == null) return 0;
    try {
      return Integer.parseInt(cursor);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("malformed cursor");
    }
  }
}
