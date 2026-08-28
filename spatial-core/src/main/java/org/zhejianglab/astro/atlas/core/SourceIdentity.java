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

package org.zhejianglab.astro.atlas.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class SourceIdentity {
  private SourceIdentity() {}

  public static String canonicalize(String sourceUri) {
    if (sourceUri == null || sourceUri.isBlank()) throw new IllegalArgumentException("source URI must not be blank");
    String value = sourceUri.trim();
    URI parsed;
    try {
      parsed = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("source URI is invalid", exception);
    }
    if (parsed.getScheme() == null) return canonicalizeLocalPath(value);
    if ("file".equalsIgnoreCase(parsed.getScheme())) {
      try {
        return Paths.get(parsed).toAbsolutePath().normalize().toUri().toString();
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("file URI is invalid", exception);
      }
    }
    String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
    String host = parsed.getHost() == null ? null : parsed.getHost().toLowerCase(Locale.ROOT);
    String path = parsed.getPath() == null ? "" : parsed.getPath();
    try {
      String normalizedPath = new URI(null, null, path, null).normalize().getPath();
      if (normalizedPath == null) normalizedPath = "";
      if (!normalizedPath.isEmpty() && !normalizedPath.startsWith("/")) normalizedPath = "/" + normalizedPath;
      return new URI(scheme, host, normalizedPath, null).toString();
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("source URI is invalid", exception);
    }
  }

  public static String canonicalize(Path path) {
    if (path == null) throw new IllegalArgumentException("path must not be null");
    return path.toAbsolutePath().normalize().toUri().toString();
  }

  public static String fileId(String sourceUri) {
    return sha256(canonicalize(sourceUri));
  }

  public static String coverageId(String layerId, String fileId, int order, long cell, CoverageRole role) {
    if (layerId == null || layerId.isBlank()) throw new IllegalArgumentException("layer ID must not be blank");
    if (fileId == null || fileId.isBlank()) throw new IllegalArgumentException("file ID must not be blank");
    if (role == null) throw new IllegalArgumentException("coverage role is required");
    return sha256(layerId + "\n" + fileId + "\n" + order + "\n" + cell + "\n" + role.value());
  }

  public static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String canonicalizeLocalPath(String value) {
    return canonicalize(Paths.get(value));
  }
}
