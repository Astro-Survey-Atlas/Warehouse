<!--
Copyright 2026 Astro Survey Atlas contributors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Contract Probe Results (2026-08-25; OSS root inventory refreshed 2026-08-26)

These are real-data probes against the v2 scanner. All probes used the
shaded `scanner-cli` runner with `--memory`, so they did not write the
Warehouse Elasticsearch indices. The reported coverage is the scanner's
actual ICRS/NESTED output, not a hand-authored expectation. Temporary samples
and credentials were kept outside the repository.

## Summary

| Probe | Source shape | Mode | Result | Coverage | Rows / errors |
| --- | --- | --- | ---: | ---: | ---: |
| HST ACS drizzled image | MAST FITS header snapshot, primary + truncated SCI extension | `fits-wcs` | unsupported | 0 | 1 file, 1 error |
| HST ACS drizzled image | same input | `fits-header-position` | unsupported | 0 | 1 file, 1 error |
| SDSS spectrum | local public FITS spectrum | `fits-header-position` | completed | 1 O8 | 1 file, 0 errors |
| Gaia DR3 | 128-row ESA TAP CSV (`source_id,ra,dec,phot_g_mean_mag`) | `catalog-radec` | completed | 12 O8 | 128 valid, 0 invalid |
| HEALPix catalog | MinIO smoke object (`source_id,hpix`) | `catalog-healpix` | completed | 3 source O8 | 3 valid, 0 invalid |
| HI4PI | CDS TAN cube header snapshot (`NAXIS=3`, spatial TAN + `VRAD`) | `fits-wcs` | completed | 7,494 O8 | 1 file, 0 errors |

The HST process terminated with:

```text
scan produced no coverage: FITS spatial header position is missing
```

This is an intentional unsupported-case result. The real HST file has
`NAXIS=0` in its primary HDU and puts the image WCS in the `SCI` extension;
the current header-only extractor reads the first HDU only. It must not be
advertised as an HST footprint until multi-HDU WCS selection is implemented
and separately verified.

The HI4PI result records an earlier extraction behavior for a spectral cube:
spatial axes 1/2 were sampled, the spectral `VRAD` axis was ignored, and all
7,494 cells were `estimated` at output order 8. Current strict validation
requires an explicit ICRS frame, so this historical input is now retained as
failed evidence rather than advertised as supported coverage.

## Input References

- HST: MAST `mast:HST/product/j8bt01010_drz.fits`; the probe file was a
  1 MiB public HTTP range containing the primary header and the beginning of
  the `SCI` extension, SHA-256
  `7c2c6930e14a7c90b300389fc886cd1185aa934c1aa8666a9c999e1dde2cdaea`.
  It is a parser-boundary probe, not a replacement for the full 53 MiB file.
- Gaia: ESA Gaia DR3 TAP sample, 128 rows, SHA-256
  `49cf5d4b46583fa8b270451e2d8175b2f354c025b2d5c6a3106b9750096b748d`.
- HI4PI: CDS `J/A+A/594/A116/CUBES/EQ2000/TAN/TAN_I07.fits`; the probe file
  was the first 1 MiB (enough for its complete primary header), SHA-256
  `abb201bd1a29c1b16c1382b474805a13ea0deefc47a48b30f3f5d1f2894f9e4b`.
- HEALPix: `s3://astro-artifacts/astro/smoke/custom-healpix-smoke.csv`,
  three rows with an explicit NESTED order-8 `hpix` column.

## Euclid OSS Inventory And Partial Scan

The supplied OSS root prefix
`projects/CSST/shared-data/euclid/aws-mirrors/q1/MER/` contains 352 tile
prefixes and 15,948 FITS objects totaling `20,239,755,819,840` bytes (about
19 TiB). The connector-only inventory completed through S3 `ListObjectsV2`;
it did not download any object content. Of those objects, 2,908 names are
`CATALOG-PSF` products. The instrument directories are DECAM (4,400), NISP
(6,336), VIS (2,112), GPC (620), HSC (1,240), and MEGACAM (1,240). This root
inventory is the reason a full scan is intentionally out of scope for a
contract probe.

The bounded tile inventory under
`projects/CSST/shared-data/euclid/aws-mirrors/q1/MER/102018212/` contains 44
FITS objects totaling `54,735,572,160` bytes (50.976 GiB), split into
`DECAM=20`, `NISP=18`, and `VIS=6`; eight names are `CATALOG-PSF` FITS tables
and the other 36 are image/mosaic products. The bounded probes below used
exact object keys, so they exercised the real OSS connector without pulling
the complete tile directory.

| Product key (under the prefix) | Mode | Result | Coverage |
| --- | --- | ---: | ---: |
| `VIS/EUC_MER_BGMOD-VIS_TILE102018212-3293DE_20241018T201846.882386Z_00.00.fits` | `fits-wcs` | completed | 11 O8, estimated |
| same VIS BGMOD object | `fits-header-position` | completed | 1 O8, entrypoint-only |
| `VIS/EUC_MER_MOSAIC-VIS-RMS_TILE102018212-D0DB63_20241018T151431.516116Z_00.00.fits` | `fits-wcs` | completed | 11 O8, estimated |
| `NISP/EUC_MER_BGMOD-NIR-H_TILE102018212-B71936_20241018T150434.158885Z_00.00.fits` | `fits-wcs` | completed | 11 O8, estimated |
| `DECAM/EUC_MER_BGMOD-DES-G_TILE102018212-969C6B_20241018T150726.525094Z_00.00.fits` | `fits-wcs` | completed | 11 O8, estimated |
| `VIS/EUC_MER_CATALOG-PSF-VIS_TILE102018212-CDF15E_20241018T211747.886023Z_00.00.fits` | `fits-wcs` | unsupported | 0, missing header position |
| `NISP/EUC_MER_CATALOG-PSF-NIR-H_TILE102018212-781884_20241018T211511.060490Z_00.00.fits` | `fits-wcs` | unsupported | 0, missing header position |

The two `CATALOG-PSF` inputs are real Euclid catalog FITS files. Their failure
is useful evidence: the current v1 modes can scan CSV/TSV catalogs, but do not
pretend that a FITS binary-table catalog is an image WCS. A future FITS-table
catalog recipe needs an explicit table/column contract; this probe does not
add that functionality.

Each successful Euclid probe discovered and processed one file, emitted zero
item errors, and reported only order 8. The four successful products all
covered the same tile footprint, as expected, while retaining distinct source
file identities. The complete root inventory was retained as an external
probe artifact; the scanner never attempted to fetch the roughly 19 TiB
dataset.

## Reproduction

Use the plan files generated for this run under `/tmp` with the current
runner, for example:

```bash
set -a; . /home/aaron/Repo/Astro-Survey-Atlas-Warehouse/.env; set +a
java -jar scanner-cli/target/scanner-cli-0.1.0-SNAPSHOT-runner.jar \
  --plan /tmp/probe-gaia-radec.json --memory
```

The Euclid plans use the exact OSS key as `source.location.prefix` and the
credential environment references only; no key value is written to a plan or
log.
