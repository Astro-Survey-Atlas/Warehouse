package org.zhejianglab.astro.atlas.scanner;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

/** S3-compatible listing and content access for S3 and Alibaba OSS. */
public final class S3SourceAdapter implements SourceAdapter {
  private final S3Client client;
  private final SourceType sourceType;

  private S3SourceAdapter(S3Client client, SourceType sourceType) {
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
  public List<InputItem> enumerate(ScanPlan plan) {
    String bucket = plan.source().location().bucket();
    String prefix = plan.source().location().prefix();
    List<InputItem> items = new ArrayList<>();
    String token = null;
    do {
      ListObjectsV2Request.Builder request = ListObjectsV2Request.builder().bucket(bucket);
      if (prefix != null && !prefix.isBlank()) request.prefix(prefix);
      if (token != null) request.continuationToken(token);
      ListObjectsV2Response response = client.listObjectsV2(request.build());
      for (S3Object object : response.contents()) {
        String key = object.key();
        if (!isSupported(key, plan) || isExcluded(key, plan)) continue;
        items.add(toInputItem(bucket, key, object.size(), object.lastModified()));
      }
      token = response.isTruncated() ? response.nextContinuationToken() : null;
    } while (token != null && !token.isBlank());
    return items.stream().sorted(java.util.Comparator.comparing(InputItem::sourceUri)).toList();
  }

  @Override
  public SourceContent open(InputItem item) {
    URI uri = URI.create(item.sourceUri());
    String bucket = uri.getHost();
    String key = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
    return () -> {
      ResponseInputStream<GetObjectResponse> response = client.getObject(
          GetObjectRequest.builder().bucket(bucket).key(key).build());
      return response;
    };
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
