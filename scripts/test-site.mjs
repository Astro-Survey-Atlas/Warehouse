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

// No project dependency required. Set PLAYWRIGHT_MODULE to an installed
// playwright index.mjs (or install Playwright in a directory outside this repo).
// Optional PLAYWRIGHT_EXECUTABLE_PATH selects an existing Chromium executable.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { fileURLToPath, pathToFileURL } from "node:url";
import { resolve, extname } from "node:path";

const root = fileURLToPath(new URL("../", import.meta.url));
const site = resolve(root, "site");
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE
  ? pathToFileURL(resolve(process.env.PLAYWRIGHT_MODULE)).href : "playwright");
const schemas = Object.fromEntries(await Promise.all(["layer", "file", "coverage"].map(async key => {
  const contract = JSON.parse(await readFile(resolve(root, `contracts/index/${key}-v1.json`), "utf8"));
  return [key, contract.template.mappings.properties];
})));
const prefix = "/Warehouse/";
const server = createServer(async (req, res) => {
  try {
    const pathname = new URL(req.url, "http://localhost").pathname;
    if (!pathname.startsWith(prefix)) throw new Error("Outside repository subpath");
    const relative = decodeURIComponent(pathname.slice(prefix.length)) || "index.html";
    const target = resolve(site, relative);
    if (!target.startsWith(`${site}/`)) throw new Error("Outside site");
    const body = await readFile(target);
    res.writeHead(200, { "Content-Type": ({ ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml" })[extname(target)] || "application/octet-stream" });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
let browser;
const failures = [];
try {
  browser = await chromium.launch({ headless: true,
    ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH } : {}) });
  console.log(`Chromium ${browser.version()}`);
  const hosted = `http://127.0.0.1:${server.address().port}${prefix}`;
  for (const url of [pathToFileURL(resolve(site, "index.html")).href, hosted]) {
    for (const width of [1440, 390, 320]) {
      const label = `${url.startsWith("file:") ? "file://" : "repository-subpath"} ${width}x900`;
      const context = await browser.newContext({ viewport: { width, height: 900 }, serviceWorkers: "block", colorScheme: "dark" });
      const page = await context.newPage();
      page.setDefaultTimeout(5000);
      const errors = [];
      const requests = [];
      const external = [];
      page.on("pageerror", error => errors.push(error.message));
      page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
      page.on("requestfailed", request => errors.push(`${request.url()}: ${request.failure()?.errorText}`));
      page.on("response", response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
      page.on("request", request => requests.push(request.url()));
      await context.route("**/*", route => {
        const requested = route.request().url();
        const local = url.startsWith("file:")
          ? requested.startsWith(pathToFileURL(`${site}/`).href)
          : requested.startsWith(hosted);
        if (!local) { external.push(requested); return route.abort(); }
        return route.continue();
      });
      try {
        await page.goto(url, { waitUntil: "networkidle" });
        assert.equal(await page.title(), "Warehouse | Astro Survey Atlas");
        assert.equal(await page.locator('link[rel="stylesheet"]').evaluate(element => Boolean(element.sheet)), true);
        const overflows = new Set();
        async function checkOverflow(state) {
          const dimensions = await page.evaluate(() => ({
            viewport: document.documentElement.clientWidth,
            document: document.documentElement.scrollWidth,
            body: document.body.scrollWidth
          }));
          if (dimensions.document > dimensions.viewport || dimensions.body > dimensions.viewport) {
            overflows.add(`${state}: ${JSON.stringify(dimensions)}`);
          }
        }
        await checkOverflow("initial");
        async function checkTheme(theme, language) {
          assert.equal(await page.locator("html").getAttribute("data-theme"), theme);
          assert.equal(await page.locator("html").evaluate(element => getComputedStyle(element).colorScheme), theme);
          const text = language === "en" ? (theme === "light" ? "Dark mode" : "Light mode")
            : (theme === "light" ? "\u591c\u95f4\u6a21\u5f0f" : "\u767d\u5929\u6a21\u5f0f");
          assert.equal(await page.locator("#theme-toggle").textContent(), text);
          assert.equal(await page.locator("#theme-toggle").getAttribute("aria-label"), text);
          const logo = new URL(`assets/logo.svg${theme === "dark" ? "#night" : ""}`, url).href;
          assert.equal(await page.locator(".brand-logo").evaluate(element => element.src), logo);
          assert.equal(await page.locator('link[rel="icon"]').evaluate(element => element.href), logo);
          await page.waitForFunction(() => {
            const image = document.querySelector(".brand-logo");
            return image.complete && image.naturalWidth > 0;
          });
          await checkOverflow(`${language} ${theme}`);
        }
        assert.equal(await page.locator("html").getAttribute("lang"), "zh-CN");
        assert.equal(await page.locator("#language-toggle").textContent(), "English");
        assert.match(await page.locator("#overview-title").textContent(), /\u626b\u63cf\u4f60\u7684\u6570\u636e\u6e90/);
        await checkTheme("light", "zh"); // Light is the default even with a dark OS preference.
        const lightBackground = await page.locator("body").evaluate(element => getComputedStyle(element).backgroundColor);
        await page.getByRole("button", { name: "Switch to English", exact: true }).click();
        assert.equal(await page.locator("html").getAttribute("lang"), "en");
        assert.match(await page.locator("#overview-title").textContent(), /Scan your data sources/);
        await checkTheme("light", "en");
        const initialRequests = requests.length;
        for (const [key, properties] of Object.entries(schemas)) {
          await page.locator(`button[data-schema="${key}"]`).click();
          assert.equal(await page.locator('button[data-schema][aria-pressed="true"]').count(), 1);
          const fields = await page.locator(".schema-table tbody tr").evaluateAll(rows => Object.fromEntries(rows.map(row => [
            row.querySelector("[data-field]").dataset.field, row.querySelector(".field-type").textContent
          ])));
          assert.deepEqual(fields, Object.fromEntries(Object.entries(properties).map(([name, value]) => [name, value.type])));
          const sample = JSON.parse(await page.locator("#schema-sample").textContent());
          assert.deepEqual(Object.keys(sample).sort(), Object.keys(properties).sort());
          for (const [name, { type }] of Object.entries(properties)) {
            for (const value of Array.isArray(sample[name]) ? sample[name] : [sample[name]]) {
              if (value === null) continue;
              if (type === "keyword") assert.equal(typeof value, "string", name);
              if (type === "date") assert.ok(typeof value === "string" && Number.isFinite(Date.parse(value)), name);
              if (["integer", "long"].includes(type)) assert.ok(Number.isSafeInteger(value), name);
            }
            await page.locator(`button[data-field="${name}"]`).click();
            assert.equal(await page.locator('button[data-field][aria-pressed="true"]').count(), 1);
            assert.equal(await page.locator(".schema-line.highlighted").count(), 1);
            assert.equal(await page.locator(".schema-line.highlighted").getAttribute("data-sample-field"), name);
            assert.equal(await page.locator(".schema-line.highlighted").evaluate(element => getComputedStyle(element).fontWeight), "700");
            assert.ok((await page.locator("#schema-explanation").textContent()).startsWith(`${name} (${type}):`));
          }
          await checkOverflow(key);
        }
        for (const scenario of ["exact", "estimated", "updating", "failed", "truncated"]) {
          await page.locator("#query-scenario").selectOption(scenario);
          const result = JSON.parse(await page.locator("#query-output").textContent());
          const status = await page.locator("#query-status").textContent();
          if (["updating", "failed"].includes(scenario)) {
            assert.deepEqual(Object.keys(result).sort(), ["code", "field", "message"]);
            assert.equal(result.code, "LAYER_NOT_QUERYABLE");
            assert.ok(result.message.endsWith(scenario.toUpperCase()));
            assert.ok(!Object.hasOwn(result, "items"), "Layer errors must not masquerade as empty items");
            assert.match(status, /HTTP 409/);
          } else {
            assert.deepEqual(Object.keys(result).sort(), ["items", "limit", "nextCursor", "truncated"]);
            assert.equal(result.items.length, 1);
            const edge = result.items[0].matchingCoverage[0];
            assert.equal(edge.precision, scenario === "estimated" ? "estimated" : "exact");
            assert.equal(edge.method, scenario === "estimated" ? "fits_wcs" : "catalog_healpix");
            assert.equal(edge.role, scenario === "estimated" ? "footprint" : "occupancy");
            assert.equal(edge.order, 6);
            assert.equal(edge.pixel, 1024);
            assert.equal(result.truncated, scenario === "truncated");
            assert.equal(result.limit, scenario === "truncated" ? 1 : 100);
            assert.equal(result.nextCursor !== null, scenario === "truncated");
            assert.match(status, /HTTP 200/);
          }
          await checkOverflow(scenario);
        }
        // Deterministic clipboard branches, independent of headless OS permissions.
        for (const mode of ["success", "denied", "missing"]) {
          await page.evaluate(mode => {
            window.copiedText = null;
            Object.defineProperty(navigator, "clipboard", { configurable: true, value: mode === "missing" ? undefined : {
              async writeText(text) {
                if (mode === "denied") throw new DOMException("Denied", "NotAllowedError");
                window.copiedText = text;
              }
            } });
          }, mode);
          for (const button of await page.locator("button[data-copy-target]").all()) {
            const target = await button.getAttribute("data-copy-target");
            await page.locator("#copy-status").evaluate(element => { element.textContent = ""; });
            await button.click();
            await page.waitForFunction(() => document.getElementById("copy-status").textContent !== "");
            const status = await page.locator("#copy-status").textContent();
            if (mode === "success") {
              assert.equal(status, "Copied to clipboard.");
              assert.equal(await page.evaluate(() => window.copiedText), await page.locator(`#${target}`).textContent());
            } else assert.match(status, /Clipboard unavailable or permission denied/);
            await checkOverflow(`copy ${mode} ${target}`);
          }
        }
        assert.deepEqual(external, [], "No external requests");
        assert.equal(requests.length, initialRequests, "Interactions must not send requests");
        // Exercise re-rendered content and preserve technical payloads across both languages.
        await page.locator('button[data-schema="coverage"]').click();
        await page.locator('button[data-field="precision"]').click();
        await page.locator("#query-scenario").selectOption("estimated");
        const sample = await page.locator("#schema-sample").textContent();
        const query = await page.locator("#query-output").textContent();
        const englishExplanation = await page.locator("#schema-explanation").textContent();
        const englishStatus = await page.locator("#query-status").textContent();
        for (const language of ["zh", "en"]) {
          await page.locator("#language-toggle").click();
          assert.equal(await page.locator("html").getAttribute("lang"), language === "zh" ? "zh-CN" : "en");
          assert.equal(await page.locator('button[data-schema][aria-pressed="true"]').getAttribute("data-schema"), "coverage");
          assert.equal(await page.locator('button[data-field][aria-pressed="true"]').getAttribute("data-field"), "precision");
          assert.equal(await page.locator("#query-scenario").inputValue(), "estimated");
          assert.equal(await page.locator("#schema-sample").textContent(), sample);
          assert.equal(await page.locator("#query-output").textContent(), query);
          const explanation = await page.locator("#schema-explanation").textContent();
          const status = await page.locator("#query-status").textContent();
          if (language === "zh") {
            assert.match(explanation, /\u8986\u76d6\u7cbe\u5ea6/);
            assert.match(status, /\u4ec5\u4e3a\u79bb\u7ebf\u793a\u4f8b/);
            assert.equal(await page.locator(".schema-table th").first().textContent(), "\u5b57\u6bb5");
            // New selections must also render in Chinese, not only existing content.
            await page.locator('button[data-schema="file"]').click();
            await page.locator('button[data-field="file_name"]').click();
            assert.match(await page.locator("#schema-explanation").textContent(), /\u5df2\u53d1\u73b0\u6e90\u6587\u4ef6\u7684\u540d\u79f0/);
            await page.locator("#query-scenario").selectOption("failed");
            assert.match(await page.locator("#query-status").textContent(), /FAILED \u8868\u793a\u4e0d\u53ef\u7528/);
            await page.locator('[data-copy-target="schema-sample"]').click();
            assert.match(await page.locator("#copy-status").textContent(), /\u526a\u8d34\u677f\u4e0d\u53ef\u7528/);
            await page.locator('button[data-schema="coverage"]').click();
            await page.locator('button[data-field="precision"]').click();
            await page.locator("#query-scenario").selectOption("estimated");
          } else {
            assert.equal(explanation, englishExplanation);
            assert.equal(status, englishStatus);
            assert.equal(await page.locator(".schema-table th").first().textContent(), "Field");
            assert.match(await page.locator("#copy-status").textContent(), /Clipboard unavailable or permission denied/);
          }
          await checkTheme("light", language);
        }
        // The preference renderer reassigns the local logo on language changes.
        const logoAsset = new URL("assets/logo.svg", url).href;
        assert.deepEqual(requests.slice(initialRequests).filter(request => request.split("#")[0] !== logoAsset), [],
          "Preference interactions may only request the local logo");
        await page.locator("#theme-toggle").click();
        await checkTheme("dark", "en");
        assert.notEqual(await page.locator("body").evaluate(element => getComputedStyle(element).backgroundColor), lightBackground);
        assert.deepEqual(requests.slice(initialRequests).filter(request => request.split("#")[0] !== logoAsset), [],
          "Theme interactions may only request the local logo");
        await page.reload({ waitUntil: "networkidle" });
        assert.equal(await page.locator("html").getAttribute("lang"), "en");
        await checkTheme("dark", "en");
        await page.locator("#language-toggle").click();
        await checkTheme("dark", "zh");
        await page.locator("#theme-toggle").click();
        await checkTheme("light", "zh");
        await page.reload({ waitUntil: "networkidle" });
        assert.equal(await page.locator("html").getAttribute("lang"), "zh-CN");
        assert.match(await page.locator("#overview-title").textContent(), /\u626b\u63cf\u4f60\u7684\u6570\u636e\u6e90/);
        await checkTheme("light", "zh");
        assert.equal(await page.locator("body").evaluate(element => getComputedStyle(element).backgroundColor), lightBackground);
        assert.deepEqual(external, [], "No external requests including theme changes and reloads");
        assert.deepEqual(errors, [], "No browser errors or failed local resources");
        console.log(`PASS ${label}: functional checks and request/error monitoring`);
        assert.equal(overflows.size, 0, `horizontal page overflow: ${[...overflows].join(", ")}`);
        console.log(`PASS ${label}: 3 contract schemas/37 fields in English, 5 queries, all copy controls x 3 clipboard modes, light default, dark toggle, bilingual dynamic content, preference reloads, logo fragments, no external/content-interaction requests or overflow/errors`);
      } catch (error) {
        failures.push(`${label}: ${error.stack}`);
        console.error(`FAIL ${label}: ${error.message}`);
      } finally {
        await context.close();
      }
    }
  }
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
assert.equal(failures.length, 0, failures.join("\n\n"));
