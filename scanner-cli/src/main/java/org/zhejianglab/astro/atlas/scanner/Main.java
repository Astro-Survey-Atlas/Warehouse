package org.zhejianglab.astro.atlas.scanner;

import java.nio.file.Path;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CredentialResolver;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.ScanPlanValidator;
import org.zhejianglab.astro.atlas.core.SourceType;
import org.zhejianglab.astro.atlas.es.ElasticsearchAdapter;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    if ((args.length != 2 && args.length != 3) || !"--plan".equals(args[0])) {
      throw new IllegalArgumentException("usage: scanner-cli --plan <plan.json> [--memory]");
    }
    boolean memory = args.length == 3 && "--memory".equals(args[2]);
    if (args.length == 3 && !memory) throw new IllegalArgumentException("unknown option: " + args[2]);
    ScanPlan plan = new ScanPlanLoader().load(Path.of(args[1]));
    ScanPlanValidator.validate(plan, memory);
    try (SourceAdapter source = switch (plan.source().connector().type()) {
      case LOCAL -> new LocalSourceAdapter();
      case S3, OSS -> S3SourceAdapter.fromPlan(plan);
    }) {
      if (memory) {
        InMemoryIndex index = new InMemoryIndex();
        printSummary(new ScanService(source, index).scan(plan, true));
        System.out.println("layers=" + index.layers().stream().map(layer -> layer.layerId() + ":" + layer.state()).toList());
        System.out.println("coverageCells=" + index.coverages().stream()
            .map(coverage -> coverage.healpixOrder() + "/" + coverage.healpixCell())
            .distinct().sorted().collect(java.util.stream.Collectors.joining(",")));
        return;
      }
      Map<String, String> credentials = CredentialResolver.resolve(plan.sink().connector().credentialRef());
      try (ElasticsearchAdapter writer = new ElasticsearchAdapter(
          plan.sink().connector().endpoint(), credentials.get("username"), credentials.get("password"))) {
        printSummary(new ScanService(source, writer).scan(plan));
      }
    }
  }

  private static void printSummary(ScanSummary summary) {
    System.out.println("phase=" + summary.phase() + " scanRunId=" + summary.scanRunId()
        + " layerId=" + summary.layerId() + " snapshot=" + summary.sourceSnapshotSha256()
        + " discovered=" + summary.discoveredFileCount()
        + " processed=" + summary.processedItemCount() + " coverage=" + summary.coverageRecordCount()
        + " catalogRows=" + summary.catalogRowCount() + " catalogValid=" + summary.validCatalogRowCount()
        + " catalogInvalid=" + summary.invalidCatalogRowCount() + " errors=" + summary.errorCount()
        + " orders=" + summary.availableOrders() + " evidence=" + summary.evidencePath());
  }
}
