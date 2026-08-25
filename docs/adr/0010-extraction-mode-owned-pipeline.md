# ExtractionMode Owns Internal Processing

Status: accepted.

ScanPlan declares one typed ExtractionMode rather than an ordered list of
Handlers. Warehouse resolves that mode to a compiled CoverageExtractor and owns
parsing, normalization, error handling, and stage order behind that interface.
This prevents callers from constructing invalid step combinations and lets the
implementation change without changing every ScanRequest. Selecting an existing
mode is runtime configuration; adding a new mode or extractor requires a normal
build and scanner image release.
