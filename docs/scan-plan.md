# ScanPlan v2 Contract

ScanPlan describes one finite refresh of one CoverageLayer. Version 1 plans are
rejected with a migration error because public Handler ordering was removed.

```json
{
  "version": 2,
  "scanRunId": "gaia-dr3-20260825-01",
  "layer": {
    "layerId": "gaia-dr3-source-catalog",
    "surveyId": "gaia",
    "releaseId": "dr3",
    "productId": "gaia-source",
    "modality": "catalog",
    "coverageRole": "occupancy",
    "entrypoint": "https://gea.esac.esa.int/archive/"
  },
  "source": {
    "connector": {"type": "s3", "endpoint": "https://object.example.invalid"},
    "location": {"bucket": "public-catalog", "prefix": "gaia/dr3/"}
  },
  "filters": {"includeSuffixes": [".csv"]},
  "extraction": {
    "mode": "catalog-radec",
    "outputOrder": 8,
    "catalog": {"raColumn": "ra", "decColumn": "dec"}
  },
  "sink": {
    "connector": {"type": "elasticsearch", "endpoint": "https://search.example.invalid"}
  },
  "evidence": {"outputPath": "/var/lib/atlas-evidence/gaia-dr3-20260825-01"}
}
```

## Extraction Modes

| Mode | Required settings | Meaning |
| --- | --- | --- |
| `fits-wcs` | `outputOrder` | Rasterize supported FITS WCS without reading arrays; sampled output is estimated |
| `fits-header-position` | `outputOrder` | Index an explicit FITS header position as entrypoint-only evidence |
| `catalog-radec` | `outputOrder`, RA and Dec columns | Map catalog coordinates to occupancy cells |
| `catalog-healpix` | pixel column and exactly one fixed order or order column | Preserve catalog NESTED order/ipix values |

`catalog-radec` uses the shared ICRS angular boundary conversion
`theta=(90-Dec)*pi/180`, `phi=RA*pi/180` before calculating the NESTED cell.
The conversion must remain identical to Assets Core so exact cell identities are
stable when Workspace imports Warehouse evidence.

Unsupported formats for the selected mode remain FileAssets with extraction
evidence but no invented SpatialCoverage. FITS WCS mode never silently falls
back to a center point.

## Validation

- Version is exactly 2 and scanRunId/layer identity use stable lowercase IDs.
- Layer modality and coverage role use controlled values.
- Source and sink credentials are references to environment variables or files.
- Mode-specific fields are required and irrelevant fields are rejected.
- HEALPix orders are explicit and supported; catalog-healpix never defaults an
  absent source order to 8.
- Evidence output is required for persisted scans and optional only for
  `--memory` diagnostics.
- In a Kubernetes `ScanRequest`, `spec.scanner.evidence.claimName` must mount
  the evidence root (default `/var/lib/atlas-evidence`); the Operator rejects
  an output path outside that root. The Claim is namespace-local and is not an
  Elasticsearch index.
- Validation completes before source enumeration or credentialed I/O.

## Runtime Summary

The scanner reports phase, scanRunId, layerId, source snapshot SHA-256,
discovered/processed/file/coverage counts, available orders, catalog row counts,
error count, evidence path, and completion time. It never reports secrets.

## Cluster Defaults

The checked-in Kubernetes examples run in `atlas-warehouse` and target the
self-managed Elasticsearch Service at
`http://atlas-warehouse-elasticsearch.atlas-warehouse.svc.cluster.local:9200`.
S3-compatible plans may target the chart-owned MinIO Service at
`http://atlas-warehouse-minio.atlas-warehouse.svc.cluster.local:9000`. These
are deployment values, not alternate source or index semantics; callers may
provide another endpoint explicitly, but the legacy `warehouse` endpoints are
not valid defaults.
