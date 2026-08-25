package org.zhejianglab.astro.atlas.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

final class EvidenceWriter {
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  EvidenceResult write(Path root, String scanRunId, String layerId, List<FileAsset> files,
      List<SpatialCoverage> coverages, List<String> errors) {
    try {
      Files.createDirectories(root);
      List<Map<String, Object>> inventory = new ArrayList<>();
      for (FileAsset file : files) {
        inventory.add(Map.of(
            "fileId", file.fileId(),
            "sourceUri", file.sourceUri(),
            "fileName", file.fileName(),
            "fileType", file.fileType().name(),
            "sizeBytes", file.sizeBytes() == null ? -1L : file.sizeBytes(),
            "lastModified", file.lastModified() == null ? "" : file.lastModified().toString()));
      }
      String inventoryJson = mapper.writeValueAsString(inventory);
      String snapshotHash = sha256(inventoryJson);
      write(root.resolve("source-inventory.json"), inventoryJson);
      writeCompressed(root.resolve("source-inventory.json.gz"), inventoryJson);
      write(root.resolve("errors.json"), mapper.writeValueAsString(errors == null ? List.of() : errors));
      Map<String, Object> normalized = new LinkedHashMap<>();
      normalized.put("schemaVersion", 1);
      normalized.put("scanRunId", scanRunId);
      normalized.put("layerId", layerId);
      normalized.put("sourceSnapshot", Map.of("sha256", snapshotHash, "fileCount", files.size()));
      normalized.put("files", files.stream().map(FileAsset::toDocument).toList());
      normalized.put("coverage", coverages.stream().map(SpatialCoverage::toDocument).toList());
      write(root.resolve("normalized-scan.json"), mapper.writeValueAsString(normalized));
      return new EvidenceResult(snapshotHash, root.toString());
    } catch (IOException exception) {
      throw new IllegalStateException("failed to write scan evidence", exception);
    }
  }

  private void write(Path path, String content) throws IOException {
    Files.writeString(path, content + "\n", StandardCharsets.UTF_8);
  }

  private void writeCompressed(Path path, String content) throws IOException {
    try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
      output.write((content + "\n").getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  record EvidenceResult(String snapshotSha256, String path) {}
}
