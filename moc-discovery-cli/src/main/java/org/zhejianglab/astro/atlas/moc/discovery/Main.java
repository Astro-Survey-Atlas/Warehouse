package org.zhejianglab.astro.atlas.moc.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || !"--survey-name".equals(args[0])) throw new IllegalArgumentException("usage: moc-discovery-cli --survey-name <name> [--release <hint>] [--product <hint>] [--output <path>] [--plan-only]");
    String survey = args[1]; String release = null; String product = null; Path output = null; boolean planOnly = false;
    for (int i = 2; i < args.length; i++) {
      switch (args[i]) {
        case "--release" -> release = args[++i];
        case "--product" -> product = args[++i];
        case "--output" -> output = Path.of(args[++i]);
        case "--plan-only" -> planOnly = true;
        default -> throw new IllegalArgumentException("unknown option: " + args[i]);
      }
    }
    DiscoveryPolicy policy = DiscoveryPolicy.cdsPublicMocV1();
    DiscoveryExecutionPlan plan = DiscoveryPlanBuilder.build(new DiscoveryIntent(survey, release, product, policy.id()), policy);
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 1); document.put("kind", planOnly ? "moc-discovery-execution-plan" : "moc-discovery-evidence");
    Map<String, Object> intent = new LinkedHashMap<>(); intent.put("surveyName", survey); if (release != null) intent.put("releaseHint", release); if (product != null) intent.put("productHint", product); document.put("intent", intent);
    document.put("policy", Map.of("id", policy.id(), "maxCandidates", policy.maxCandidates(), "maxProbes", policy.maxProbes(), "maxRequests", policy.maxRequests(), "maxObjectBytes", policy.maxObjectBytes(), "maxTaskBytes", policy.maxTaskBytes(), "maxOrder", policy.maxOrder()));
    document.put("searchUri", plan.searchUri().toString()); document.put("probeUris", plan.probeUris().stream().map(Object::toString).toList());
    if (!planOnly) {
      Map<String, Object> evidence = new MocDiscoveryWorker(policy).run(plan);
      evidence.put("executionPlan", document);
      document = evidence;
    }
    String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
    if (output == null) System.out.print(json); else {
      Path parent = output.toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(output, json);
    }
  }
}
