package org.zhejianglab.astro.atlas.moc.discovery;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class DiscoveryPlanBuilder {
  private DiscoveryPlanBuilder() {}

  public static DiscoveryExecutionPlan build(DiscoveryIntent intent, DiscoveryPolicy policy) {
    if (!policy.id().equals(intent.policyRef())) throw new IllegalArgumentException("unsupported discovery policy: " + intent.policyRef());
    StringBuilder query = new StringBuilder("https://alasky.cds.unistra.fr/MocServer/query?REQUEST=queryData&LANG=ADQL&FORMAT=json&QUERY=");
    String adql = "SELECT * FROM ivoa.ObsCore WHERE 1=1 AND (lower(obs_title) LIKE '%" + escape(intent.surveyName().toLowerCase()) + "%'";
    if (intent.releaseHint() != null) adql += " OR lower(obs_collection) LIKE '%" + escape(intent.releaseHint().toLowerCase()) + "%'";
    if (intent.productHint() != null) adql += " OR lower(obs_id) LIKE '%" + escape(intent.productHint().toLowerCase()) + "%'";
    adql += ")";
    URI search = URI.create(query + URLEncoder.encode(adql, StandardCharsets.UTF_8));
    return new DiscoveryExecutionPlan(intent, policy, search, List.of());
  }

  private static String escape(String value) { return value.replace("'", "''").replace("%", ""); }
}
