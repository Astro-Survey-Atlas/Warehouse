# Current CoverageLayer State With Explicit Multi-Order Cells

Status: accepted; supersedes ADR-0003 and ADR-0004.

Warehouse stores only the current searchable state of each CoverageLayer.
Every SpatialCoverage record keeps its real ICRS/NESTED `order/ipix` and
precision; a coarse source cell is never expanded and presented as a finer
measurement. Layer refreshes are hidden while `UPDATING`, become searchable
only when `ACTIVE`, and become explicitly unavailable when `FAILED`.

Stable URI-derived FileAsset IDs remain global, while coverage IDs also include
the layer ID. This allows a file to be referenced by more than one layer without
mixing their replacement lifecycles. The fixed `ast_*` names remain isolated
from legacy `astro_*` indices; their `v1` suffix is a mapping version, not a
scan-history version.
