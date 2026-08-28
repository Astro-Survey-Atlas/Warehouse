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

package org.zhejianglab.astro.atlas.operator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class KubeNames {
  private KubeNames() {}

  public static String scannerJobName(String requestName, String planHash) {
    String hash = planHash.substring(0, 10);
    String prefix = dnsLabel(requestName, 47);
    return dnsLabel(prefix + "-scan-" + hash, 63);
  }

  public static String planConfigMapName(String jobName) {
    return dnsLabel("plan-" + shortHash(jobName), 63);
  }

  public static String dnsLabel(String value, int maxLength) {
    String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-")
        .replaceAll("-+", "-");
    if (normalized.isBlank()) normalized = "scan";
    normalized = trimDashes(normalized);
    if (normalized.length() > maxLength) normalized = trimDashes(normalized.substring(0, maxLength));
    return normalized.isBlank() ? "scan" : normalized;
  }

  public static String shortHash(String value) {
    return sha256(value).substring(0, 12);
  }

  public static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte item : digest) result.append(String.format("%02x", item));
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String trimDashes(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && value.charAt(start) == '-') start++;
    while (end > start && value.charAt(end - 1) == '-') end--;
    return value.substring(start, end);
  }
}
