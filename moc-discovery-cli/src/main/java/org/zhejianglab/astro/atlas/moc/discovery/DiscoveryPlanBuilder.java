/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zhejianglab.astro.atlas.moc.discovery;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DiscoveryPlanBuilder {
  private static final String MOC_SERVER_QUERY = "https://alasky.cds.unistra.fr/MocServer/query";
  private static final String RECORD_FIELDS = String.join(",",
      "ID", "creator_did", "publisher_did", "obs_id", "obs_title", "obs_collection",
      "moc_access_url", "hips_service_url", "access_url", "web_access_url");

  private DiscoveryPlanBuilder() {}

  public static DiscoveryExecutionPlan build(DiscoveryIntent intent, DiscoveryPolicy policy) {
    if (!policy.id().equals(intent.policyRef())) throw new IllegalArgumentException("unsupported discovery policy: " + intent.policyRef());
    String survey = filterTerm(intent.surveyName(), "surveyName");
    List<String> hints = new ArrayList<>();
    if (intent.releaseHint() != null) hints.add(filterTerm(intent.releaseHint(), "releaseHint"));
    if (intent.productHint() != null) hints.add(filterTerm(intent.productHint(), "productHint"));

    StringBuilder expression = new StringBuilder("obs_collection=*").append(survey).append("*");
    if (!hints.isEmpty()) {
      expression.append(" && (");
      for (int index = 0; index < hints.size(); index++) {
        if (index > 0) expression.append(" || ");
        String hint = hints.get(index);
        expression.append("obs_collection=*").append(hint).append("*")
            .append(" || obs_title=*").append(hint).append("*")
            .append(" || obs_id=*").append(hint).append("*")
            .append(" || ID=*").append(hint).append("*");
      }
      expression.append(")");
    }
    expression.append(" && (moc_access_url=* || hips_service_url=*)");
    String query = MOC_SERVER_QUERY
        + "?expr=" + encode(expression.toString())
        + "&get=record&fmt=json&MAXREC=" + policy.maxCandidates()
        + "&casesensitive=false&fields=" + encode(RECORD_FIELDS);
    URI search = URI.create(query);
    return new DiscoveryExecutionPlan(intent, policy, search, List.of());
  }

  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

  static String recordUrl(String id) {
    return MOC_SERVER_QUERY + "?ID=" + encode(id) + "&get=record&fmt=json";
  }

  static String spatialMocUrl(String id, int order) {
    return MOC_SERVER_QUERY + "?ID=" + encode(id) + "&get=smoc&order=" + order + "&fmt=fits";
  }

  private static String filterTerm(String value, String field) {
    StringBuilder safe = new StringBuilder();
    for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
      if (Character.isLetterOrDigit(character) || Character.isWhitespace(character)
          || character == '-' || character == '_' || character == '.' || character == '/' || character == ':') {
        safe.append(character);
      } else {
        safe.append(' ');
      }
    }
    String normalized = safe.toString().replaceAll("\\s+", " ").trim();
    if (normalized.isBlank()) throw new IllegalArgumentException(field + " contains no searchable characters");
    return normalized;
  }
}
