/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(() => {
  "use strict";

  const t = text => window.WarehouseI18n?.t(text) ?? text;
  const translate = root => window.WarehouseI18n?.translate(root);

  // Illustrative data only, not a survey inventory or a live query result.
  const fileId = "5c74d6cae60fe3f77011eefefe40a9a1f87640d8fa4cfbab69995f830d8ebcee";
  const sourceUri = "https://example.org/survey/tile.fits";
  const layerId = "example-r1-catalog";
  const timestamp = "2026-09-01T00:00:00Z";

  // Field tuples: mapped name, Elasticsearch type, explanation, sample value.
  // Keep aligned with contracts/index/{layer,file,coverage}-v1.json.
  const schemas = {
    layer: {
      index: "ast_layer_index_v1",
      title: "CoverageLayer",
      description: "Current state of one survey, release, and product. Only ACTIVE layers are queryable; this document is not a MOC or a coverage-cell list.",
      identity: "_id = layer_id",
      fields: [
        ["layer_id", "keyword", "Stable CoverageLayer identity and Elasticsearch document ID.", layerId],
        ["survey_id", "keyword", "Survey identity for this layer.", "example"],
        ["release_id", "keyword", "Survey release identity, not an index mapping version or scan run.", "r1"],
        ["product_id", "keyword", "Product identity within the survey release.", "catalog"],
        ["modality", "keyword", "Declared data kind: image, spectrum, cube, catalog, timeseries, visibility, event, or other.", "catalog"],
        ["coverage_role", "keyword", "Spatial meaning of the association: footprint or occupancy.", "occupancy"],
        ["entrypoint", "keyword", "Optional official product/download URL. Assets may label this entrypoint-only when file-level reverse mapping is unavailable; the API does not manufacture an edge.", "https://example.org/survey/"],
        ["state", "keyword", "ACTIVE, UPDATING, or FAILED. UPDATING and FAILED are explicit query errors, never empty coverage results.", "ACTIVE"],
        ["scan_run_id", "keyword", "Execution identity for the current refresh, not a historical index generation.", "example-scan-001"],
        ["lease_expires_at", "date", "Expiring refresh lease required for UPDATING; null for ACTIVE or FAILED.", null],
        ["source_snapshot_sha256", "keyword", "SHA-256 of the consumed source inventory, not a raw scientific-file checksum. Full inventory and errors remain in external evidence. This sample digest is illustrative.", "0123456789abcdef".repeat(4)],
        ["available_orders", "integer", "Array of available explicit NESTED HEALPix orders. Elasticsearch maps each value as integer, not as a separate array type. Coarse data cannot satisfy finer-order queries.", [6]],
        ["file_count", "long", "Nonnegative current-layer file count. FileAsset identities are global, not layer-owned.", 1],
        ["coverage_count", "long", "Nonnegative count of current SpatialCoverage edges, not unique pixels or catalog rows.", 1],
        ["error_count", "integer", "Nonnegative number of recorded scan/extraction errors; details are retained as evidence.", 0],
        ["error_summary", "keyword", "Credential-free failure summary; null when no failure summary is present.", null],
        ["updated_at", "date", "Timestamp of the current layer-state update.", timestamp]
      ]
    },
    file: {
      index: "ast_file_index_v1",
      title: "FileAsset",
      description: "Global identity and source metadata for one discovered file. No raw payload, modality, extraction mode, layer ID, or coverage list is stored here.",
      identity: "_id = sha256(canonical_source_uri)",
      fields: [
        ["file_id", "keyword", "SHA-256 of the canonical source URI; also the document ID. Not a content checksum and not derived from a layer ID.", fileId],
        ["source_uri", "keyword", "Canonical source URI of the discovered file, without credential values. Warehouse does not proxy downloads.", sourceUri],
        ["file_name", "keyword", "Name of the discovered source file.", "tile.fits"],
        ["parent_uri", "keyword", "Parent location of the canonical source URI.", "https://example.org/survey/"],
        ["file_type", "keyword", "Detected file type, serialized using the FileType enum name (for example FITS). Not an ExtractionMode.", "FITS"],
        ["size_bytes", "long", "Source file size in bytes, nullable when unknown.", 2880],
        ["last_modified", "date", "Source last-modified timestamp, nullable when unknown.", timestamp],
        ["indexed_at", "date", "Latest FileAsset indexing timestamp.", timestamp]
      ]
    },
    coverage: {
      index: "ast_coverage_index_v1",
      title: "SpatialCoverage",
      description: "One layer-to-file association at one explicit ICRS, NESTED HEALPix cell. It is not an inverted array of file IDs or an exact geometry guarantee.",
      identity: "_id is deterministic for (layer_id, source_file_id, healpix_order, healpix_cell, coverage_role)",
      fields: [
        ["layer_id", "keyword", "CoverageLayer identity; the layer must be ACTIVE before this edge can participate in a query.", layerId],
        ["source_file_id", "keyword", "Associated global FileAsset ID, derived from its canonical source URI.", fileId],
        ["source_uri", "keyword", "Canonical source URI for the associated discovered file, without credential values.", sourceUri],
        ["healpix_order", "integer", "Actual stored HEALPix order. Reverse lookup matches this explicit order; coarse cells are never expanded into invented finer coverage.", 6],
        ["healpix_cell", "long", "NESTED pixel index (ipix) at healpix_order; valid range is 0 through 12 * 4^order - 1.", 1024],
        ["coordinate_frame", "keyword", "Explicit celestial reference frame: ICRS.", "ICRS"],
        ["nesting", "keyword", "Explicit HEALPix numbering scheme: NESTED.", "NESTED"],
        ["coverage_method", "keyword", "Extraction method: fits_wcs, fits_header_position, catalog_radec, or catalog_healpix. Indexed values use underscores, unlike ScanPlan mode values.", "catalog_healpix"],
        ["coverage_role", "keyword", "Association role: footprint or occupancy. This role participates in edge identity.", "occupancy"],
        ["modality", "keyword", "Declared layer modality copied onto the association, not inferred from the FileAsset.", "catalog"],
        ["precision", "keyword", "Coverage precision: exact, estimated, or entrypoint-only. Response truncation is separate; exact cell evidence is not exact polygon refinement.", "exact"],
        ["source_order", "integer", "Optional original source HEALPix order, retained when applicable; null when no source order is supplied. Finer inputs may be coarsened, never the reverse.", 6]
      ]
    }
  };

  function renderSchema(key, selectedField) {
    const container = document.getElementById("schema-content");
    if (!container || !Object.hasOwn(schemas, key)) return;
    const schema = schemas[key];
    document.querySelectorAll("button[data-schema]").forEach(button => {
      const selected = button.dataset.schema === key;
      button.classList.toggle("active", selected);
      button.setAttribute("aria-pressed", String(selected));
    });

    // Only fixed, local dictionary strings enter this markup. JSON uses textContent.
    container.innerHTML = `
      <h3>${schema.title} <code>${schema.index}</code></h3>
      <p>${schema.description}</p>
      <p><code>${schema.identity}</code></p>
      <p>Strict mapping: unknown fields are rejected. All keyword fields use ignore_above: 2048.
      The v1 suffix versions mappings, not scan runs. Select a field to inspect its example.</p>
      <div style="overflow-x:auto">
        <table class="schema-table">
          <thead><tr><th scope="col">Field</th><th scope="col">Mapped type</th><th scope="col">Description</th></tr></thead>
          <tbody>${schema.fields.map(([name, type, description]) => `
            <tr><td><button type="button" class="field-name btn btn-secondary" data-field="${name}"
              aria-controls="schema-sample schema-explanation" aria-pressed="false">${name}</button></td>
              <td><span class="field-type">${type}</span></td><td>${description}</td></tr>`).join("")}</tbody>
        </table>
      </div>
      <p id="schema-explanation" class="callout callout-info" role="status" aria-live="polite"></p>
      <div class="code-header"><span>Illustrative document (not live data)</span>
        <button type="button" class="copy-btn" data-copy-target="schema-sample">Copy JSON</button></div>
      <pre class="code-content" style="max-width:100%;overflow-x:auto"><code id="schema-sample" class="language-json"></code></pre>`;

    const sample = container.querySelector("#schema-sample");
    const values = Object.fromEntries(schema.fields.map(([name, , , value]) => [name, value]));
    const lines = JSON.stringify(values, null, 2).split("\n");
    lines.forEach((line, index) => {
      const span = document.createElement("span");
      span.className = "schema-line";
      const field = schema.fields.find(([name]) => line.startsWith(`  "${name}":`));
      if (field) span.dataset.sampleField = field[0];
      span.textContent = line + (index < lines.length - 1 ? "\n" : "");
      sample.append(span);
    });

    function selectField(name) {
      const field = schema.fields.find(([fieldName]) => fieldName === name);
      if (!field) return;
      container.querySelectorAll("button[data-field]").forEach(button => {
        const selected = button.dataset.field === name;
        button.classList.toggle("active", selected);
        button.setAttribute("aria-pressed", String(selected));
      });
      sample.querySelectorAll(".schema-line").forEach(line => {
        const selected = line.dataset.sampleField === name;
        line.classList.toggle("highlighted", selected);
        line.style.backgroundColor = selected ? "var(--brand-magenta-glow, #613047)" : "";
        line.style.fontWeight = selected ? "700" : "";
      });
      container.querySelector("#schema-explanation").textContent = `${field[0]} (${field[1]}): ${t(field[2])}`;
    }
    container.querySelectorAll("button[data-field]").forEach(button => {
      button.addEventListener("click", () => selectField(button.dataset.field));
    });
    selectField(selectedField ?? schema.fields[0][0]);
    translate(container);
  }

  function renderQuery() {
    const select = document.getElementById("query-scenario");
    const output = document.getElementById("query-output");
    const status = document.getElementById("query-status");
    if (!select || !output || !status) return;
    const scenario = select.value;
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    if (!["exact", "estimated", "updating", "failed", "truncated"].includes(scenario)) {
      output.textContent = "";
      status.textContent = t("Choose an offline fixture. No request is sent.");
      return;
    }

    let body;
    let explanation;
    if (scenario === "updating" || scenario === "failed") {
      const state = scenario.toUpperCase();
      body = {
        code: "LAYER_NOT_QUERYABLE",
        message: `layer ${layerId} is not queryable: ${state}`,
        field: layerId
      };
      explanation = `HTTP 409 example: ${state} is unavailable, not empty coverage.`;
    } else {
      const estimated = scenario === "estimated";
      const truncated = scenario === "truncated";
      body = {
        items: [{
          fileId,
          sourceUri,
          fileName: "tile.fits",
          fileType: "FITS",
          sizeBytes: 2880,
          lastModified: timestamp,
          matchingCoverage: [{
            layerId: estimated ? "example-r1-image" : layerId,
            order: 6,
            pixel: 1024,
            method: estimated ? "fits_wcs" : "catalog_healpix",
            role: estimated ? "footprint" : "occupancy",
            precision: estimated ? "estimated" : "exact",
            sourceOrder: estimated ? null : 6
          }]
        }],
        limit: truncated ? 1 : 100,
        nextCursor: truncated ? "illustrative-opaque-cursor-not-for-submission" : null,
        truncated
      };
      explanation = truncated
        ? "HTTP 200 example: limited response, truncated=true; precision remains exact. The opaque cursor shown is a non-executable placeholder."
        : estimated
          ? "HTTP 200 example: estimated WCS candidate, not an exact geometry claim; response is not truncated."
          : "HTTP 200 example: exact cell occupancy at order 6, pixel 1024; response is not truncated. This is a candidate lookup, not polygon refinement.";
    }
    output.textContent = JSON.stringify(body, null, 2);
    status.textContent = `${t("Offline fixture only. No network request or live execution.")} ${t(explanation)}`;
  }

  function init() {
    document.querySelectorAll("button[data-schema]").forEach(button => {
      button.addEventListener("click", () => renderSchema(button.dataset.schema));
    });
    renderSchema("layer");
    document.getElementById("query-scenario")?.addEventListener("change", renderQuery);
    renderQuery();
    document.addEventListener("warehouse-language-change", () => {
      const key = document.querySelector('button[data-schema][aria-pressed="true"]')?.dataset.schema;
      const field = document.querySelector('button[data-field][aria-pressed="true"]')?.dataset.field;
      if (key) renderSchema(key, field);
      renderQuery();
    });

    // Delegation also covers the copy button recreated by schema selection.
    document.addEventListener("click", async event => {
      const button = event.target instanceof Element
        ? event.target.closest("button[data-copy-target]") : null;
      if (!button) return;
      event.preventDefault();
      const status = document.getElementById("copy-status");
      if (status) {
        status.setAttribute("role", "status");
        status.setAttribute("aria-live", "polite");
      }
      const target = document.getElementById(button.dataset.copyTarget);
      if (!target) {
        if (status) status.textContent = "Copy unavailable: the target element was not found.";
        translate();
        return;
      }
      try {
        if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
        await navigator.clipboard.writeText(target.textContent);
        if (status) status.textContent = "Copied to clipboard.";
      } catch {
        if (status) status.textContent = "Clipboard unavailable or permission denied. Select the displayed text and copy it manually.";
      }
      translate();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
