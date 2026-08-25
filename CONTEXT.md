# Warehouse Domain Context

This glossary defines the language shared by scanner plans, indexed documents,
the Operator, and Assets reverse lookup. Implementation details belong under
`docs/`.

## Language

**CoverageLayer**:
One survey, release, and product whose coverage and file associations are
refreshed as one current-state unit.
_Avoid_: Scan version, index generation

**FileAsset**:
One actually discovered public or configured file, identified by its canonical
source URI and described without storing or proxying its scientific payload.
_Avoid_: SourceUnit, object row

**SpatialCoverage**:
An association between a CoverageLayer, a FileAsset, and one explicit ICRS,
NESTED HEALPix `order/ipix` cell, with method, role, and precision.
_Avoid_: Order-8 approximation, geometry blob

**ExtractionMode**:
The declared spatial meaning of a scan input, such as FITS WCS or catalog
RA/Dec. The scanner owns all internal processing steps and their order.
_Avoid_: Handler list, pipeline script

**Modality**:
The controlled kind of data represented by a CoverageLayer: image, spectrum,
cube, catalog, timeseries, visibility, event, or other.
_Avoid_: Wavelength band, automatic file classification

**Entrypoint**:
An official product or download URL used when file-level spatial reverse
mapping is unavailable.
_Avoid_: Synthetic FileAsset

**SourceSnapshot**:
The named, SHA-256-identified inventory consumed by one scan run, including
enumeration and extraction errors retained as evidence.
_Avoid_: Historical search result

**ScanPlan**:
The finite intent for refreshing one CoverageLayer from one source using one
ExtractionMode and writing current index documents plus evidence.
_Avoid_: Workflow, DAG

**ScanRequest**:
A Kubernetes submission of one ScanPlan and its execution policy, credential
references, Job, and observable run status.

**CoverageCandidate**:
A FileAsset returned because a current SpatialCoverage cell intersects the
requested cell at the same explicit order; it is not an exact geometry claim.

**SourceUnit**:
A reserved future observation, tile, exposure, or logical object that may group
multiple FileAssets. It is not implemented in v1.
_Avoid_: FileAsset alias
