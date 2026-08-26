# Contract Probes

These probes validate that the scanner only needs public metadata to build a
spatial directory. They deliberately do not read image pixels, spectral
samples, flux values, or cube planes.

| Dataset shape | Extraction mode | Metadata read | Expected result |
| --- | --- | --- | --- |
| HST complex image WCS | `fits-wcs` | `CTYPE*`, `CRVAL*`, `CRPIX*`, `CD*`/`CDELT*`, `NAXIS1/2` | sampled ICRS/NESTED cells with `estimated` precision, or an item error for unsupported WCS |
| SDSS spectral FITS | `fits-header-position` | header position keys only | one `entrypoint-only` cell; no wavelength/flux access |
| Gaia catalog shard | `catalog-radec` | header plus configured RA/Dec columns | deduplicated occupancy cells with `exact` precision |
| HI4PI spectral cube | `fits-wcs` | cube WCS and spatial `NAXIS*` metadata | spatial cells from header geometry; spectral axes are ignored |

## Running A Probe

Use a v2 ScanPlan whose source connector points at a public local/object-store
mirror. For a persisted run, configure `spec.scanner.evidence` with a PVC
mounted at the evidence root and set `evidence.outputPath` below that root; run
the scanner with `--memory` first when testing locally. Inspect
`source-inventory.json`,
`normalized-scan.json`, and `errors.json`; the evidence must contain public URI,
header/catalog metadata, output order, precision, and any unsupported-case
error, but never credentials or science-array values.

The production gate is the same plan against the configured public endpoint.
That deployment-backed run is intentionally separate from unit tests because
the endpoint, mirror availability, and credentials are environment state.

The completed 2026-08-25 run, including real Euclid OSS inventory counts,
successful mode summaries, input hashes, and unsupported HST/FITS-catalog
cases, is recorded in
[`contract-probe-results-20260825.md`](contract-probe-results-20260825.md).
