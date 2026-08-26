package org.zhejianglab.astro.atlas.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.zhejianglab.astro.atlas.core.FileAsset;
import org.zhejianglab.astro.atlas.core.SpatialCoverage;

/** Writes evidence incrementally so failures remain observable without retaining a scan. */
final class EvidenceWriter {
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  Session start(Path root, String scanRunId, String layerId) {
    return new Session(root, scanRunId, layerId);
  }

  final class Session implements AutoCloseable {
    private final Path root;
    private final String scanRunId;
    private final String layerId;
    private final Path filesPath;
    private final Path coveragePath;
    private final MessageDigest snapshotDigest = digest();
    private BufferedWriter inventory;
    private BufferedWriter compressedInventory;
    private BufferedWriter errors;
    private BufferedWriter files;
    private BufferedWriter coverages;
    private boolean firstInventory = true;
    private boolean firstErrorWritten = true;
    private boolean streamsClosed;
    private String phase = "STARTED";
    private String firstError;
    private long fileCount;
    private long coverageCount;
    private long errorCount;
    private String snapshotSha256;
    private int catalogRows;
    private int validCatalogRows;
    private int invalidCatalogRows;

    private Session(Path root, String scanRunId, String layerId) {
      this.root = root;
      this.scanRunId = scanRunId;
      this.layerId = layerId;
      this.filesPath = root.resolve(".files.ndjson");
      this.coveragePath = root.resolve(".coverage.ndjson");
      try {
        Files.createDirectories(root);
        inventory = Files.newBufferedWriter(root.resolve("source-inventory.json"), StandardCharsets.UTF_8);
        OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(root.resolve("source-inventory.json.gz")));
        compressedInventory = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8));
        errors = Files.newBufferedWriter(root.resolve("errors.json"), StandardCharsets.UTF_8);
        files = Files.newBufferedWriter(filesPath, StandardCharsets.UTF_8);
        coverages = Files.newBufferedWriter(coveragePath, StandardCharsets.UTF_8);
        inventory.write('[');
        compressedInventory.write('[');
        errors.write('[');
        writeSummary();
      } catch (IOException exception) {
        closeQuietly();
        throw new IllegalStateException("failed to initialize scan evidence", exception);
      }
    }

    void phase(String value) {
      phase = value;
      writeSummary();
    }

    void record(FileAsset file, List<SpatialCoverage> coverage) {
      ensureOpen();
      try {
        Map<String, Object> inventoryRecord = new LinkedHashMap<>();
        inventoryRecord.put("fileId", file.fileId());
        inventoryRecord.put("sourceUri", file.sourceUri());
        inventoryRecord.put("fileName", file.fileName());
        inventoryRecord.put("fileType", file.fileType().name());
        inventoryRecord.put("sizeBytes", file.sizeBytes() == null ? -1L : file.sizeBytes());
        inventoryRecord.put("lastModified", file.lastModified() == null ? "" : file.lastModified().toString());
        writeArrayValue(inventory, compressedInventory, mapper.writeValueAsString(inventoryRecord));
        String inventoryJson = mapper.writeValueAsString(inventoryRecord);
        snapshotDigest.update(inventoryJson.getBytes(StandardCharsets.UTF_8));
        snapshotDigest.update((byte) '\n');
        files.write(mapper.writeValueAsString(file.toDocument()));
        files.newLine();
        if (coverage != null) {
          for (SpatialCoverage item : coverage) {
            coverages.write(mapper.writeValueAsString(item.toDocument()));
            coverages.newLine();
          }
        }
        fileCount++;
        coverageCount += coverage == null ? 0 : coverage.size();
        if (fileCount % 100 == 0) writeSummary();
      } catch (IOException exception) {
        throw new IllegalStateException("failed to append scan evidence", exception);
      }
    }

    void error(String message) {
      ensureOpen();
      String value = message == null || message.isBlank() ? "unknown scan error" : message;
      if (firstError == null) firstError = value;
      errorCount++;
      try {
        if (!firstErrorWritten) {
          errors.write(',');
        }
        errors.write(mapper.writeValueAsString(value));
        firstErrorWritten = false;
      } catch (IOException exception) {
        throw new IllegalStateException("failed to append scan error evidence", exception);
      }
    }

    EvidenceResult complete(List<Integer> orders, int catalogRows, int validCatalogRows, int invalidCatalogRows) {
      setCatalogCounts(catalogRows, validCatalogRows, invalidCatalogRows);
      phase = "COMPLETED";
      RuntimeException failure = null;
      try { closeStreams(); } catch (RuntimeException exception) { failure = exception; }
      if (snapshotSha256 == null) snapshotSha256 = snapshotSha256();
      try { writeNormalized(orders); } catch (RuntimeException exception) { failure = combine(failure, exception); }
      try { writeSummary(); } catch (RuntimeException exception) { failure = combine(failure, exception); }
      if (failure != null) throw failure;
      return new EvidenceResult(snapshotSha256, root.toString());
    }

    EvidenceResult fail(String failure, List<Integer> orders, int catalogRows, int validCatalogRows, int invalidCatalogRows) {
      setCatalogCounts(catalogRows, validCatalogRows, invalidCatalogRows);
      RuntimeException problem = null;
      try { recordFailure(failure); } catch (RuntimeException exception) { problem = exception; }
      phase = "FAILED";
      try { closeStreams(); } catch (RuntimeException exception) { problem = combine(problem, exception); }
      if (snapshotSha256 == null) snapshotSha256 = snapshotSha256();
      try { writeNormalized(orders); } catch (RuntimeException exception) { problem = combine(problem, exception); }
      try { writeSummary(); } catch (RuntimeException exception) { problem = combine(problem, exception); }
      if (problem != null) throw problem;
      return new EvidenceResult(snapshotSha256, root.toString());
    }

    long fileCount() { return fileCount; }
    long coverageCount() { return coverageCount; }
    long errorCount() { return errorCount; }
    String firstError() { return firstError; }

    String snapshotSha256() {
      if (snapshotSha256 != null) return snapshotSha256;
      try {
        MessageDigest copy = (MessageDigest) snapshotDigest.clone();
        return hex(copy.digest());
      } catch (CloneNotSupportedException exception) {
        throw new IllegalStateException("SHA-256 digest cannot be copied", exception);
      }
    }

    @Override
    public void close() {
      if (!streamsClosed) {
        try { closeStreams(); } catch (RuntimeException ignored) {}
      }
    }

    private void writeNormalized(List<Integer> orders) {
      boolean complete = false;
      try (BufferedWriter output = Files.newBufferedWriter(root.resolve("normalized-scan.json"), StandardCharsets.UTF_8)) {
        output.write("{\"schemaVersion\":1,\"phase\":");
        output.write(mapper.writeValueAsString(phase));
        output.write(",\"scanRunId\":");
        output.write(mapper.writeValueAsString(scanRunId));
        output.write(",\"layerId\":");
        output.write(mapper.writeValueAsString(layerId));
        output.write(",\"sourceSnapshot\":");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sha256", snapshotSha256);
        snapshot.put("fileCount", fileCount);
        snapshot.put("coverageCount", coverageCount);
        snapshot.put("errorCount", errorCount);
        snapshot.put("availableOrders", orders == null ? List.of() : orders);
        snapshot.put("catalogRows", catalogRows);
        snapshot.put("catalogValid", validCatalogRows);
        snapshot.put("catalogInvalid", invalidCatalogRows);
        output.write(mapper.writeValueAsString(snapshot));
        output.write(",\"files\":");
        copyNdjsonArray(filesPath, output);
        output.write(",\"coverage\":");
        copyNdjsonArray(coveragePath, output);
        output.write("}\n");
        complete = true;
      } catch (IOException exception) {
        throw new IllegalStateException("failed to finalize normalized scan evidence", exception);
      } finally {
        if (complete) {
          deleteQuietly(filesPath);
          deleteQuietly(coveragePath);
        }
      }
    }

    private void copyNdjsonArray(Path path, Writer output) throws IOException {
      output.write('[');
      boolean first = true;
      try (BufferedReader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        String line;
        while ((line = input.readLine()) != null) {
          if (line.isBlank()) continue;
          if (!first) output.write(',');
          output.write(line);
          first = false;
        }
      }
      output.write(']');
    }

    private void writeArrayValue(Writer first, Writer second, String value) throws IOException {
      if (!firstInventory) {
        first.write(',');
        second.write(',');
      }
      first.write(value);
      second.write(value);
      firstInventory = false;
    }

    private void writeSummary() {
      if (root == null) return;
      try {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", 1);
        summary.put("phase", phase);
        summary.put("scanRunId", scanRunId);
        summary.put("layerId", layerId);
        summary.put("fileCount", fileCount);
        summary.put("coverageCount", coverageCount);
        summary.put("errorCount", errorCount);
        summary.put("firstError", firstError);
        summary.put("sourceSnapshotSha256", snapshotSha256);
        summary.put("catalogRowCount", catalogRows);
        summary.put("validCatalogRowCount", validCatalogRows);
        summary.put("invalidCatalogRowCount", invalidCatalogRows);
        summary.put("updatedAt", Instant.now());
        Files.writeString(root.resolve("summary.json"), mapper.writeValueAsString(summary) + "\n", StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw new IllegalStateException("failed to write scan evidence summary", exception);
      }
    }

    private void closeStreams() {
      if (streamsClosed) return;
      streamsClosed = true;
      RuntimeException failure = null;
      try {
        if (inventory != null) { inventory.write(']'); inventory.close(); }
      } catch (IOException exception) {
        failure = new IllegalStateException("failed to close scan evidence", exception);
      }
      try {
        if (compressedInventory != null) { compressedInventory.write(']'); compressedInventory.close(); }
      } catch (IOException exception) { failure = combine(failure, new IllegalStateException("failed to close scan evidence", exception)); }
      try {
        if (errors != null) { errors.write(']'); errors.close(); }
      } catch (IOException exception) { failure = combine(failure, new IllegalStateException("failed to close scan evidence", exception)); }
      try { if (files != null) files.close(); }
      catch (IOException exception) { failure = combine(failure, new IllegalStateException("failed to close scan evidence", exception)); }
      try { if (coverages != null) coverages.close(); }
      catch (IOException exception) { failure = combine(failure, new IllegalStateException("failed to close scan evidence", exception)); }
      if (failure != null) throw failure;
    }

    private void ensureOpen() {
      if (streamsClosed) throw new IllegalStateException("scan evidence session is closed");
    }

    private void setCatalogCounts(int rows, int valid, int invalid) {
      catalogRows = rows;
      validCatalogRows = valid;
      invalidCatalogRows = invalid;
    }

    private void recordFailure(String failure) {
      if (failure == null || failure.isBlank() || errorCount > 0) return;
      String value = failure.trim();
      if (!streamsClosed) {
        error(value);
        return;
      }
      try {
        Files.writeString(root.resolve("errors.json"), mapper.writeValueAsString(List.of(value)) + "\n", StandardCharsets.UTF_8);
        firstError = value;
        errorCount = 1;
      } catch (IOException exception) {
        throw new IllegalStateException("failed to persist terminal scan error evidence", exception);
      }
    }

    private RuntimeException combine(RuntimeException first, RuntimeException second) {
      if (first == null) return second;
      first.addSuppressed(second);
      return first;
    }

    private void closeQuietly() {
      try { if (inventory != null) inventory.close(); } catch (IOException ignored) {}
      try { if (compressedInventory != null) compressedInventory.close(); } catch (IOException ignored) {}
      try { if (errors != null) errors.close(); } catch (IOException ignored) {}
      try { if (files != null) files.close(); } catch (IOException ignored) {}
      try { if (coverages != null) coverages.close(); } catch (IOException ignored) {}
    }
  }

  record EvidenceResult(String snapshotSha256, String path) {}

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String hex(byte[] value) {
    return java.util.HexFormat.of().formatHex(value);
  }

  private static void deleteQuietly(Path path) {
    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
  }
}
