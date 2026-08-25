package org.zhejianglab.astro.atlas.query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.IndexReader;
import org.zhejianglab.astro.atlas.core.Page;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;
import org.zhejianglab.astro.atlas.core.SpatialQuery;

/** Read-only coverage-to-file join used by every HTTP search endpoint. */
public final class QueryService {
  private final IndexReader reader;

  public QueryService(IndexReader reader) {
    if (reader == null) throw new IllegalArgumentException("index reader is required");
    this.reader = reader;
  }

  public FileSearchResponse search(SpatialQuery query) {
    if (query == null) throw new IllegalArgumentException("query is required");
    Page<SpatialCoverage> coveragePage = reader.searchCoverage(query.order8Cells(), query.limit(), query.cursor());
    Map<String, List<MatchingCoverage>> matchingByFile = new LinkedHashMap<>();
    for (SpatialCoverage coverage : coveragePage.items()) {
      matchingByFile.computeIfAbsent(coverage.sourceFileId(), ignored -> new java.util.ArrayList<>())
          .add(MatchingCoverage.from(coverage));
    }

    Collection<FileAsset> files = reader.findFiles(matchingByFile.keySet());
    Map<String, FileAsset> filesById = new LinkedHashMap<>();
    for (FileAsset file : files) filesById.put(file.fileId(), file);

    List<FileSearchItem> items = matchingByFile.entrySet().stream()
        .filter(entry -> filesById.containsKey(entry.getKey()))
        .map(entry -> FileSearchItem.from(filesById.get(entry.getKey()), entry.getValue()))
        .toList();
    return new FileSearchResponse(items, query.limit(), coveragePage.nextCursor());
  }

  public boolean isReady() {
    return reader.isReady();
  }
}
