package org.zhejianglab.astro.atlas.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.zhejianglab.astro.atlas.core.CoverageMethod;
import org.zhejianglab.astro.atlas.core.CoverageRole;
import org.zhejianglab.astro.atlas.core.FileType;
import org.zhejianglab.astro.atlas.core.Healpix;

/** Reads FITS header blocks only; image and spectral arrays are never loaded. */
public final class FitsHeaderHandler implements Handler {
  private static final int BLOCK_SIZE = 2880;
  private static final int CARD_SIZE = 80;
  private static final int MAX_HEADER_BLOCKS = 256;

  @Override
  public void handle(ScanContext context) throws IOException {
    if (context.item().fileType() != FileType.FITS) return;
    Map<String, String> header = readHeader(context);
    Double ra;
    Double dec;
    try {
      ra = firstDouble(header, "CRVAL1", "RA", "RA_DEG");
      dec = firstDouble(header, "CRVAL2", "DEC", "DEC_DEG");
    } catch (IllegalArgumentException exception) {
      context.addError(exception.getMessage());
      return;
    }
    if (ra == null || dec == null) return;
    try {
      Wcs wcs = Wcs.from(header);
      if (wcs == null) {
        if (Wcs.hasGeometry(header)) {
          context.addError("unsupported or invalid FITS WCS geometry");
          return;
        }
        context.addCoverage(Healpix.ang2pixNest(8, ra, dec), CoverageMethod.WCS, CoverageRole.FOOTPRINT, "header_point");
        return;
      }
      for (long cell : wcs.coverageCells()) {
        context.addCoverage(cell, CoverageMethod.WCS, CoverageRole.FOOTPRINT, "wcs_footprint");
      }
    } catch (IllegalArgumentException exception) {
      context.addError("invalid FITS spatial value: " + exception.getMessage());
    }
  }

  private static Map<String, String> readHeader(ScanContext context) throws IOException {
    Map<String, String> header = new LinkedHashMap<>();
    try (InputStream input = context.content().open()) {
      byte[] block = new byte[BLOCK_SIZE];
      for (int blockNumber = 0; blockNumber < MAX_HEADER_BLOCKS; blockNumber++) {
        int offset = 0;
        while (offset < block.length) {
          int read = input.read(block, offset, block.length - offset);
          if (read < 0) return header;
          offset += read;
        }
        for (int cardOffset = 0; cardOffset < block.length; cardOffset += CARD_SIZE) {
          String card = new String(block, cardOffset, CARD_SIZE, StandardCharsets.US_ASCII);
          String key = card.substring(0, 8).trim();
          if ("END".equals(key)) return header;
          if (card.length() > 9 && card.charAt(8) == '=' && !key.isEmpty()) {
            String value = card.substring(10);
            int comment = value.indexOf('/');
            header.put(key, (comment < 0 ? value : value.substring(0, comment)).trim());
          }
        }
      }
    }
    return header;
  }

  private static Double firstDouble(Map<String, String> header, String... keys) {
    for (String key : keys) {
      String value = header.get(key);
      if (value == null) continue;
      try {
        return Double.parseDouble(value.replace('D', 'E').replace('d', 'e'));
      } catch (NumberFormatException ignored) {
        throw new IllegalArgumentException("malformed FITS spatial value for " + key);
      }
    }
    return null;
  }

  /** A linear TAN WCS is sufficient for the header-only MVP and avoids reading image data. */
  private static final class Wcs {
    private static final double TARGET_SAMPLE_DEG = 0.08;
    private static final int MAX_SAMPLES = 100_000;

    private final double ra0;
    private final double dec0;
    private final double crpix1;
    private final double crpix2;
    private final double width;
    private final double height;
    private final double cd11;
    private final double cd12;
    private final double cd21;
    private final double cd22;
    private final boolean tan;

    private Wcs(Map<String, String> header, double[] matrix) {
      ra0 = number(header, "CRVAL1");
      dec0 = number(header, "CRVAL2");
      crpix1 = number(header, "CRPIX1");
      crpix2 = number(header, "CRPIX2");
      width = number(header, "NAXIS1");
      height = number(header, "NAXIS2");
      cd11 = matrix[0];
      cd12 = matrix[1];
      cd21 = matrix[2];
      cd22 = matrix[3];
      String ctype = (header.getOrDefault("CTYPE1", "") + header.getOrDefault("CTYPE2", ""))
          .toUpperCase(java.util.Locale.ROOT);
      tan = ctype.isBlank() || ctype.contains("TAN");
    }

    private static Wcs from(Map<String, String> header) {
      if (!hasAll(header, "CRVAL1", "CRVAL2", "CRPIX1", "CRPIX2", "NAXIS1", "NAXIS2")) return null;
      String ctype1 = header.getOrDefault("CTYPE1", "").toUpperCase(java.util.Locale.ROOT);
      String ctype2 = header.getOrDefault("CTYPE2", "").toUpperCase(java.util.Locale.ROOT);
      if ((!ctype1.isBlank() && !ctype1.contains("TAN"))
          || (!ctype2.isBlank() && !ctype2.contains("TAN"))) return null;
      try {
        double[] matrix = matrix(header);
        Wcs wcs = new Wcs(header, matrix);
        double determinant = wcs.cd11 * wcs.cd22 - wcs.cd12 * wcs.cd21;
        if (!(wcs.width > 0.0 && wcs.height > 0.0)
            || !Double.isFinite(wcs.width) || !Double.isFinite(wcs.height)
            || !Double.isFinite(wcs.crpix1) || !Double.isFinite(wcs.crpix2)
            || !Double.isFinite(wcs.cd11) || !Double.isFinite(wcs.cd12)
            || !Double.isFinite(wcs.cd21) || !Double.isFinite(wcs.cd22)
            || !Double.isFinite(determinant) || Math.abs(determinant) < 1e-20) return null;
        if (!Double.isFinite(wcs.ra0)) throw new IllegalArgumentException("WCS CRVAL1 is not finite");
        Healpix.validateDeclination(wcs.dec0);
        return wcs;
      } catch (RuntimeException exception) {
        return null;
      }
    }

    private static boolean hasGeometry(Map<String, String> header) {
      return hasAll(header, "CRPIX1", "CRPIX2", "NAXIS1", "NAXIS2")
          && (hasAll(header, "CD1_1", "CD1_2", "CD2_1", "CD2_2")
              || hasAll(header, "CDELT1", "CDELT2"));
    }

    private LinkedHashSet<Long> coverageCells() {
      double estimatedX = Math.max(1.0, Math.ceil(width * pixelScaleX() / TARGET_SAMPLE_DEG));
      double estimatedY = Math.max(1.0, Math.ceil(height * pixelScaleY() / TARGET_SAMPLE_DEG));
      double estimatedSamples = (estimatedX + 1.0) * (estimatedY + 1.0);
      double reduction = estimatedSamples > MAX_SAMPLES
          ? Math.sqrt(estimatedSamples / MAX_SAMPLES)
          : 1.0;
      int xSteps = boundedSteps(estimatedX, reduction);
      int ySteps = boundedSteps(estimatedY, reduction);
      LinkedHashSet<Long> cells = new LinkedHashSet<>();
      for (int y = 0; y <= ySteps; y++) {
        double pixelY = 0.5 + height * y / (double) ySteps;
        for (int x = 0; x <= xSteps; x++) {
          double pixelX = 0.5 + width * x / (double) xSteps;
          double[] point = world(pixelX, pixelY);
          cells.add(Healpix.ang2pixNest(8, point[0], point[1]));
        }
      }
      return cells;
    }

    private int boundedSteps(double estimated, double reduction) {
      if (!Double.isFinite(estimated) || !Double.isFinite(reduction) || reduction <= 0.0) return 1;
      double steps = Math.max(1.0, Math.ceil(estimated / reduction));
      return (int) Math.min(MAX_SAMPLES, steps);
    }

    private double pixelScaleX() {
      return Math.max(1e-12, Math.hypot(cd11, cd21));
    }

    private double pixelScaleY() {
      return Math.max(1e-12, Math.hypot(cd12, cd22));
    }

    private double[] world(double x, double y) {
      double xi = Math.toRadians(cd11 * (x - crpix1) + cd12 * (y - crpix2));
      double eta = Math.toRadians(cd21 * (x - crpix1) + cd22 * (y - crpix2));
      double ra0Radians = Math.toRadians(ra0);
      double dec0Radians = Math.toRadians(dec0);
      double raRadians;
      double decRadians;
      if (tan) {
        double denominator = Math.cos(dec0Radians) - eta * Math.sin(dec0Radians);
        raRadians = ra0Radians + Math.atan2(xi, denominator);
        decRadians = Math.atan2(
            Math.sin(dec0Radians) + eta * Math.cos(dec0Radians),
            Math.sqrt(denominator * denominator + xi * xi));
      } else {
        raRadians = ra0Radians + xi / Math.max(1e-8, Math.cos(dec0Radians));
        decRadians = dec0Radians + eta;
      }
      double ra = Healpix.normalizeRa(Math.toDegrees(raRadians));
      double dec = Math.toDegrees(decRadians);
      if (!Double.isFinite(ra) || !Double.isFinite(dec)) throw new IllegalArgumentException("WCS produced a non-finite coordinate");
      return new double[] {ra, Math.max(-90.0, Math.min(90.0, dec))};
    }

    private static double[] matrix(Map<String, String> header) {
      if (hasAll(header, "CD1_1", "CD1_2", "CD2_1", "CD2_2")) {
        return new double[] {
            number(header, "CD1_1"), number(header, "CD1_2"),
            number(header, "CD2_1"), number(header, "CD2_2")};
      }
      if (!hasAll(header, "CDELT1", "CDELT2")) throw new IllegalArgumentException("WCS matrix is incomplete");
      double sx = number(header, "CDELT1");
      double sy = number(header, "CDELT2");
      double angle = Math.toRadians(optionalNumber(header, "CROTA2", 0.0));
      return new double[] {
          sx * Math.cos(angle), -sy * Math.sin(angle),
          sx * Math.sin(angle), sy * Math.cos(angle)};
    }

    private static boolean hasAll(Map<String, String> header, String... keys) {
      for (String key : keys) if (!header.containsKey(key)) return false;
      return true;
    }

    private static double number(Map<String, String> header, String key) {
      String value = header.get(key);
      if (value == null) throw new IllegalArgumentException("missing WCS keyword " + key);
      return Double.parseDouble(value.replace('D', 'E').replace('d', 'e').trim());
    }

    private static double optionalNumber(Map<String, String> header, String key, double fallback) {
      String value = header.get(key);
      return value == null ? fallback : Double.parseDouble(value.replace('D', 'E').replace('d', 'e').trim());
    }
  }
}
