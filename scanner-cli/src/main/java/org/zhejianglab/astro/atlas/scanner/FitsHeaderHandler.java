package org.zhejianglab.astro.atlas.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
    Double ra = firstDouble(header, "CRVAL1", "RA", "RA_DEG");
    Double dec = firstDouble(header, "CRVAL2", "DEC", "DEC_DEG");
    if (ra == null || dec == null) return;
    try {
      context.addCoverage(Healpix.ang2pixNest(8, ra, dec), CoverageMethod.WCS, CoverageRole.FOOTPRINT, "header_point");
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
        // A malformed card is reported only when it is the selected spatial evidence.
      }
    }
    return null;
  }
}
