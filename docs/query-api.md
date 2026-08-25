# Query API Contract

## Role

`query-api` is a read-only HTTP process. It searches the two new indices and returns FileAsset candidates. It does not submit scans, mutate Elasticsearch, manage credentials, or expose a user directory.

## Endpoints

### Health

```text
GET /healthz
GET /readyz
```

`/healthz` reports process health. `/readyz` reports whether the read adapter can reach the configured Elasticsearch target.

### Point Search

```text
GET /v1/files/point?ra=180.25&dec=-2.5&limit=100&cursor=<opaque>
```

The point is converted to its ICRS NESTED order-8 cell. Results are candidates associated with that cell.

### Cone Search

```text
GET /v1/files/cone?ra=180.25&dec=-2.5&radiusDeg=0.5&limit=100&cursor=<opaque>
```

The center and radius are converted to the set of order-8 cells intersected by the query cone. Pixel-boundary candidates are accepted in the MVP.

### HEALPix Search

```text
GET /v1/files/healpix?order=8&pixel=123456&limit=100&cursor=<opaque>
```

The requested cell is normalized to the fixed order-8 index representation. Lower-order input expands to the corresponding order-8 cells, and higher-order input maps to its order-8 parent cell. The result remains a coverage candidate at order 8.

## Response

The exact JSON casing is finalized with the Java model, but the semantic shape is:

```json
{
  "items": [
    {
      "fileId": "sha256-of-canonical-uri",
      "sourceUri": "s3://survey-data/release-1/image.fits",
      "fileName": "image.fits",
      "fileType": "FITS",
      "sizeBytes": 123456,
      "lastModified": "2026-01-02T03:04:05Z",
      "modality": "image",
      "spatialStatus": "known",
      "matchingCoverage": [
        {
          "order": 8,
          "pixel": 123456,
          "method": "wcs"
        }
      ]
    }
  ],
  "limit": 100,
  "nextCursor": "opaque-cursor-or-null"
}
```

The API does not return credentials, authorization headers, or arbitrary indexed source maps.

## Pagination

- Default limit: 100.
- Maximum limit: 1000.
- Results use a stable sort including FileAsset ID as a tie-breaker.
- `nextCursor` encodes the Elasticsearch `search_after` state and query identity.
- A cursor cannot be reused with a different query. The API rejects mismatched cursor parameters.
- Deep `from/size` pagination is not supported.
- The requested `limit` applies to unique FileAsset results. Because one file may have many matching coverage cells, the service may consume multiple coverage pages internally before returning a page; `nextCursor` represents the last coverage page consumed.

## Validation

- `ra` is in the canonical longitude range defined by `spatial-core`.
- `dec` is within the valid ICRS declination range.
- `radiusDeg` is positive and subject to an implementation-defined maximum documented with the running service.
- `order` and `pixel` must be a valid HEALPix pair.
- `limit` must be between 1 and 1000.
- A malformed or mismatched cursor is a client error.

## Error Shape

Public errors should have a stable shape with a machine-readable code, a safe message, and optional field details:

```json
{
  "code": "INVALID_QUERY",
  "message": "dec must be between -90 and 90",
  "field": "dec"
}
```

Transport and Elasticsearch failures return a server-side error code without exposing endpoint credentials, request headers, or raw exception dumps.

## Access Control

The MVP query process has no user identity model. Network access, authentication, and authorization are provided by the surrounding Ingress or API Gateway. This does not make the endpoint public by default; deployment must place it behind the expected cluster access layer.
