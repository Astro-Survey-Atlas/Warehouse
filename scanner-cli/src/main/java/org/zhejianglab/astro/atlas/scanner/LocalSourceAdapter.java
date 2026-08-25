package org.zhejianglab.astro.atlas.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.InputItem;
import org.zhejianglab.astro.atlas.core.ScanPlan;
import org.zhejianglab.astro.atlas.core.SourceContent;
import org.zhejianglab.astro.atlas.core.SourceType;

public final class LocalSourceAdapter implements SourceAdapter {
  @Override
  public List<InputItem> enumerate(ScanPlan plan) {
    if (plan.source() == null || plan.source().connector() == null || plan.source().connector().type() != SourceType.LOCAL) {
      throw new IllegalArgumentException("LocalSourceAdapter requires a local source");
    }
    Path root = Path.of(plan.source().location().rootPath()).toAbsolutePath().normalize();
    if (Files.isRegularFile(root)) {
      if (!isSupported(root, plan)) return List.of();
      return List.of(toInputItem(root.getParent(), root));
    }
    if (!Files.isDirectory(root)) throw new IllegalArgumentException("local source path is not a file or directory: " + root);
    try (Stream<Path> paths = Files.walk(root)) {
      return paths.filter(Files::isRegularFile)
          .filter(path -> isSupported(path, plan))
          .filter(path -> !isExcluded(root, path, plan))
          .sorted()
          .map(path -> toInputItem(root, path))
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("failed to enumerate local source: " + root, exception);
    }
  }

  @Override
  public SourceContent open(InputItem item) {
    if (!item.sourceUri().startsWith("file:")) throw new IllegalArgumentException("item is not a local file");
    Path path = Path.of(java.net.URI.create(item.sourceUri()));
    return () -> Files.newInputStream(path);
  }

  private static boolean isSupported(Path path, ScanPlan plan) {
    List<String> suffixes = plan.filters().includeSuffixes();
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (!suffixes.isEmpty()) return suffixes.stream().anyMatch(suffix -> name.endsWith(suffix.toLowerCase(Locale.ROOT)));
    return FileType.fromFileName(name) != FileType.UNKNOWN;
  }

  private static boolean isExcluded(Path root, Path path, ScanPlan plan) {
    String relative = root.relativize(path).toString();
    return plan.filters().excludePatterns().stream()
        .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
        .anyMatch(matcher -> matcher.matches(Path.of(relative)));
  }

  private static InputItem toInputItem(Path root, Path path) {
    try {
      String parentUri = root == null ? path.getParent().toUri().toString() : path.getParent().toUri().toString();
      return new InputItem(path.toUri().toString(), path.getFileName().toString(), parentUri,
          FileType.fromFileName(path.getFileName().toString()), Files.size(path), Files.getLastModifiedTime(path).toInstant());
    } catch (IOException exception) {
      throw new IllegalStateException("failed to read local source metadata: " + path, exception);
    }
  }
}
