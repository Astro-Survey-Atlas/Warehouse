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

package org.zhejianglab.astro.atlas.scanner;

import java.net.URI;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.zhejianglab.astro.atlas.core.CredentialResolver;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceConnector;
import org.zhejianglab.astro.atlas.core.SourceContent;
import org.zhejianglab.astro.atlas.core.SourceType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-compatible listing and content access for S3 and Alibaba OSS. */
public final class S3SourceAdapter implements SourceAdapter {
  private static final long FITS_HEADER_RANGE_END = 2880L * 256L - 1L;
  private final S3Client client;
  private final SourceType sourceType;

  S3SourceAdapter(S3Client client, SourceType sourceType) {
    this.client = client;
    this.sourceType = sourceType;
  }

  public static S3SourceAdapter fromPlan(ScanPlan plan) {
    SourceConnector connector = plan.source().connector();
    if (connector.type() != SourceType.S3 && connector.type() != SourceType.OSS) {
      throw new IllegalArgumentException("S3SourceAdapter requires an s3 or oss source");
    }
    if (connector.endpoint() == null || connector.endpoint().isBlank()) {
      throw new IllegalArgumentException("object source endpoint is required");
    }
    Map<String, String> credentials = CredentialResolver.resolve(connector.credentialRef());
    software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
        .endpointOverride(URI.create(connector.endpoint()))
        .region(Region.of(connector.region() == null || connector.region().isBlank() ? "us-east-1" : connector.region()))
        .forcePathStyle(true);
    if (credentials.containsKey("accessKey") && credentials.containsKey("secretKey")) {
      builder = builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(credentials.get("accessKey"), credentials.get("secretKey"))));
    }
    return new S3SourceAdapter(builder.build(), connector.type());
  }

  @Override
  public Stream<InputItem> enumerate(ScanPlan plan) {
    String bucket = plan.source().location().bucket();
    String prefix = plan.source().location().prefix();
    Iterator<InputItem> iterator = new ListingIterator(plan, bucket, prefix);
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
  }

  @Override
  public SourceContent open(InputItem item) {
    URI uri = URI.create(item.sourceUri());
    String bucket = uri.getHost();
    String key = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
    return () -> openContent(item, bucket, key);
  }

  public void close() {
    client.close();
  }

  private InputItem toInputItem(String bucket, String key, Long size, java.time.Instant lastModified) {
    String scheme = sourceType == SourceType.OSS ? "oss" : "s3";
    String sourceUri = scheme + "://" + bucket + "/" + key;
    int slash = key.lastIndexOf('/');
    String fileName = slash < 0 ? key : key.substring(slash + 1);
    String parentUri = scheme + "://" + bucket + (slash < 0 ? "/" : "/" + key.substring(0, slash));
    return new InputItem(sourceUri, fileName, parentUri, FileType.fromFileName(fileName), size, lastModified);
  }

  private InputStream openContent(InputItem item, String bucket, String key) throws IOException {
    GetObjectRequest full = GetObjectRequest.builder().bucket(bucket).key(key).build();
    if (item.fileType() != FileType.FITS) return client.getObject(full);

    GetObjectRequest range = GetObjectRequest.builder().bucket(bucket).key(key)
        .range("bytes=0-" + FITS_HEADER_RANGE_END).build();
    try {
      ResponseInputStream<GetObjectResponse> response = client.getObject(range);
      if (hasHonoredRange(response.response())) return response;
      response.close();
    } catch (S3Exception exception) {
      if (!rangeUnsupported(exception)) throw exception;
    }
    return client.getObject(full);
  }

  private static boolean hasHonoredRange(GetObjectResponse response) {
    String contentRange = response.contentRange();
    return contentRange != null && contentRange.toLowerCase(Locale.ROOT).startsWith("bytes 0-");
  }

  private static boolean rangeUnsupported(S3Exception exception) {
    int status = exception.statusCode();
    return status == 400 || status == 416 || status == 501;
  }

  private final class ListingIterator implements Iterator<InputItem> {
    private final ScanPlan plan;
    private final String bucket;
    private final String prefix;
    private Iterator<S3Object> page = List.<S3Object>of().iterator();
    private String continuationToken;
    private boolean lastPage;
    private InputItem next;

    private ListingIterator(ScanPlan plan, String bucket, String prefix) {
      this.plan = plan;
      this.bucket = bucket;
      this.prefix = prefix;
    }

    @Override
    public boolean hasNext() {
      advance();
      return next != null;
    }

    @Override
    public InputItem next() {
      advance();
      if (next == null) throw new NoSuchElementException();
      InputItem value = next;
      next = null;
      return value;
    }

    private void advance() {
      if (next != null) return;
      while (true) {
        while (page.hasNext()) {
          S3Object object = page.next();
          String key = object.key();
          if (isSupported(key, plan) && !isExcluded(key, plan)) {
            next = toInputItem(bucket, key, object.size(), object.lastModified());
            return;
          }
        }
        if (lastPage) return;
        ListObjectsV2Request.Builder request = ListObjectsV2Request.builder().bucket(bucket);
        if (prefix != null && !prefix.isBlank()) request.prefix(prefix);
        if (continuationToken != null && !continuationToken.isBlank()) request.continuationToken(continuationToken);
        ListObjectsV2Response response = client.listObjectsV2(request.build());
        page = response.contents().iterator();
        lastPage = !response.isTruncated() || response.nextContinuationToken() == null
            || response.nextContinuationToken().isBlank();
        continuationToken = response.nextContinuationToken();
      }
    }
  }

  private static boolean isSupported(String key, ScanPlan plan) {
    String name = key.toLowerCase(Locale.ROOT);
    List<String> suffixes = plan.filters().includeSuffixes();
    if (!suffixes.isEmpty()) return suffixes.stream().anyMatch(suffix -> name.endsWith(suffix.toLowerCase(Locale.ROOT)));
    return FileType.fromFileName(name) != FileType.UNKNOWN;
  }

  private static boolean isExcluded(String key, ScanPlan plan) {
    return plan.filters().excludePatterns().stream().anyMatch(pattern -> glob(pattern, key));
  }

  private static boolean glob(String pattern, String value) {
    StringBuilder regex = new StringBuilder("^");
    for (char c : pattern.toCharArray()) {
      if (c == '*') regex.append(".*");
      else if (c == '?') regex.append('.');
      else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) regex.append('\\').append(c);
      else regex.append(c);
    }
    return value.matches(regex.append('$').toString());
  }
}
