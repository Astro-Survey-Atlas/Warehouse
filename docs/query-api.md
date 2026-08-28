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

# Diagnostic Query Contract

The Query API is a read-only diagnostic implementation of the same lookup that
Assets performs against configured Warehouse Elasticsearch. It has no scan or
write endpoint.

## HEALPix Reverse Lookup

```text
GET /v2/files/healpix?layers=<comma-list>&order=<order>&pixels=<comma-list>&limit=<n>&cursor=<opaque>
```

Every layer ID, order, and pixel is required. All layers must be ACTIVE and the
requested order must be available for them. The response contains unique files
with matching edges:

```json
{
  "items": [{
    "fileId": "...",
    "sourceUri": "https://...",
    "fileName": "example.fits",
    "fileType": "FITS",
      "matchingCoverage": [{
      "layerId": "survey-release-product",
      "order": 8,
      "pixel": 123,
      "method": "fits_wcs",
      "role": "footprint",
      "precision": "estimated"
    }]
  }],
  "limit": 100,
  "nextCursor": null,
  "truncated": false
}
```

If a layer is `UPDATING` or `FAILED`, the response is an explicit layer-state
error rather than an empty result. If only an official product entrypoint is
known, Assets reads it from the layer document and labels it `entrypoint-only`;
the API never manufactures a coverage edge.

## Compatibility Diagnostics

Point and cone endpoints may remain as order-8 diagnostic helpers for current
fixtures, but they are not the production Assets reverse-lookup contract and do
not justify coercing stored coverage to order 8.

## Limits And Errors

- Default limit is 100 and maximum is 1000.
- Cursor fingerprint includes normalized layer IDs, order, and pixels.
- Hitting a response or edge limit sets `truncated=true`.
- Invalid coordinates/cells return a stable client error.
- Layer-state and Elasticsearch failures never expose credentials or endpoints.
