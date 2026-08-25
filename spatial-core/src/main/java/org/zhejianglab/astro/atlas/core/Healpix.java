package org.zhejianglab.astro.atlas.core;

import java.util.LinkedHashSet;
import java.util.Set;

/** NESTED HEALPix angular conversion used by the fixed index contract. */
public final class Healpix {
  public static final int MAX_ORDER = 12;
  public static final int INDEX_ORDER = IndexContract.ORDER;
  public static final long INDEX_CELL_COUNT = 12L * (1L << (2 * INDEX_ORDER));

  private Healpix() {}

  public static long ang2pixNest(int order, double raDeg, double decDeg) {
    validateOrder(order);
    validateDeclination(decDeg);
    int nside = 1 << order;
    double phi = Math.toRadians(normalizeRa(raDeg));
    double z = Math.sin(Math.toRadians(decDeg));
    double za = Math.abs(z);
    double tt = modulo(phi / (Math.PI / 2), 4.0);
    int face;
    int ix;
    int iy;
    if (za <= 2.0 / 3.0) {
      double temp1 = nside * (0.5 + tt);
      double temp2 = nside * (z * 0.75);
      long jp = (long) Math.floor(temp1 - temp2);
      long jm = (long) Math.floor(temp1 + temp2);
      int ifp = (int) (jp >> order);
      int ifm = (int) (jm >> order);
      face = ifp == ifm ? (ifp | 4) : (ifp < ifm ? ifp : ifm + 8);
      ix = (int) (jm & (nside - 1));
      iy = nside - 1 - (int) (jp & (nside - 1));
    } else {
      int ntt = Math.min(3, (int) Math.floor(tt));
      double tp = tt - ntt;
      double tmp = nside * Math.sqrt(3 * (1 - za));
      int jp = Math.min(nside - 1, (int) Math.floor(tp * tmp));
      int jm = Math.min(nside - 1, (int) Math.floor((1 - tp) * tmp));
      if (z >= 0) {
        face = ntt;
        ix = nside - jm - 1;
        iy = nside - jp - 1;
      } else {
        face = ntt + 8;
        ix = jp;
        iy = jm;
      }
    }
    return ((long) face << (2 * order)) | spread(ix) | (spread(iy) << 1);
  }

  public static long normalizeCell(int order, long pixel) {
    validateOrder(order);
    long count = 12L * (1L << (2 * order));
    if (pixel < 0 || pixel >= count) throw new IllegalArgumentException("HEALPix pixel is outside its order");
    if (order == INDEX_ORDER) return pixel;
    if (order < INDEX_ORDER) return pixel << (2 * (INDEX_ORDER - order));
    return pixel >> (2 * (order - INDEX_ORDER));
  }

  public static Set<Long> normalizeQueryCells(int order, long pixel) {
    validateOrder(order);
    long count = 12L * (1L << (2 * order));
    if (pixel < 0 || pixel >= count) throw new IllegalArgumentException("HEALPix pixel is outside its order");
    Set<Long> cells = new LinkedHashSet<>();
    if (order <= INDEX_ORDER) {
      long first = pixel << (2 * (INDEX_ORDER - order));
      long children = 1L << (2 * (INDEX_ORDER - order));
      for (long offset = 0; offset < children; offset++) cells.add(first + offset);
    } else {
      cells.add(normalizeCell(order, pixel));
    }
    return Set.copyOf(cells);
  }

  /**
   * Produces a conservative order-8 cell set for a cone. Sampling includes a small
   * pixel-scale margin because the index is intentionally a candidate index.
   */
  public static Set<Long> cellsForCone(double raDeg, double decDeg, double radiusDeg) {
    validateRa(raDeg);
    validateDeclination(decDeg);
    if (!(radiusDeg > 0.0) || radiusDeg > 180.0) throw new IllegalArgumentException("radiusDeg must be > 0 and <= 180");
    Set<Long> cells = new LinkedHashSet<>();
    cells.add(ang2pixNest(INDEX_ORDER, raDeg, decDeg));
    double samplingRadius = Math.min(180.0, radiusDeg + 0.5);
    int radialSteps = Math.max(1, (int) Math.ceil(samplingRadius / 0.25));
    for (int radial = 1; radial <= radialSteps; radial++) {
      double distance = samplingRadius * radial / radialSteps;
      int azimuthSteps = Math.max(32, (int) Math.ceil(360.0 * Math.max(distance, 0.25) / 0.25));
      for (int azimuth = 0; azimuth < azimuthSteps; azimuth++) {
        double bearing = 2.0 * Math.PI * azimuth / azimuthSteps;
        double[] point = destination(raDeg, decDeg, distance, bearing);
        cells.add(ang2pixNest(INDEX_ORDER, point[0], point[1]));
      }
    }
    return Set.copyOf(cells);
  }

  public static void validateRa(double raDeg) {
    if (!Double.isFinite(raDeg) || raDeg < 0.0 || raDeg >= 360.0) throw new IllegalArgumentException("ra must be in [0, 360)");
  }

  public static void validateDeclination(double decDeg) {
    if (!Double.isFinite(decDeg) || decDeg < -90.0 || decDeg > 90.0) throw new IllegalArgumentException("dec must be in [-90, 90]");
  }

  public static double normalizeRa(double raDeg) {
    if (!Double.isFinite(raDeg)) throw new IllegalArgumentException("ra must be finite");
    double value = raDeg % 360.0;
    return value < 0 ? value + 360.0 : value;
  }

  private static void validateOrder(int order) {
    if (order < 0 || order > MAX_ORDER) throw new IllegalArgumentException("HEALPix order must be 0..12");
  }

  private static long spread(int value) {
    long output = 0;
    for (int bit = 0; bit < 16; bit++) output |= ((long) (value >> bit) & 1L) << (2 * bit);
    return output;
  }

  private static double[] destination(double raDeg, double decDeg, double distanceDeg, double bearing) {
    double lat = Math.toRadians(decDeg);
    double lon = Math.toRadians(raDeg);
    double distance = Math.toRadians(distanceDeg);
    double sinLat = Math.sin(lat) * Math.cos(distance) + Math.cos(lat) * Math.sin(distance) * Math.cos(bearing);
    double outputDec = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sinLat))));
    double y = Math.sin(bearing) * Math.sin(distance) * Math.cos(lat);
    double x = Math.cos(distance) - Math.sin(lat) * Math.sin(Math.toRadians(outputDec));
    double outputRa = normalizeRa(Math.toDegrees(lon + Math.atan2(y, x)));
    return new double[] {outputRa, outputDec};
  }

  private static double modulo(double value, double divisor) {
    double result = value % divisor;
    return result < 0 ? result + divisor : result;
  }
}
