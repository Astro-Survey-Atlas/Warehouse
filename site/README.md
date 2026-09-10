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

# Warehouse Documentation Site

Open `index.html` directly in a browser. No build, server, external fonts,
CDN, or API connection is required. Repository links need internet access;
query responses are illustrative offline fixtures, not live survey results.
Clipboard access depends on browser permissions; text remains selectable.

The default appearance is light, with the indigo/magenta Atlas mark. An optional
night theme uses navy surfaces and a periwinkle/coral mark. Header controls switch
between Chinese and English and between themes; preferences are stored locally
when browser storage is available. Code and public contract identifiers are not
translated. Translation strings live in `i18n.js`; theme behavior is in `theme.js`.

## GitHub Pages

In repository Settings > Pages, choose **GitHub Actions** as the source.
The `Documentation Pages` workflow publishes only `site/` on matching pushes
to `main`, or on manual dispatch. The expected project URL is
<https://astro-survey-atlas.github.io/Warehouse/>.
Adding the workflow does not itself enable Pages or publish this worktree.
Relative asset paths support both project Pages and direct local opening.

## Maintenance

Keep the dictionary in `main.js` aligned with `contracts/index/*-v1.json`.
Keep scan examples aligned with `docs/scan-plan.md`, `docs/operator.md`, and
`deploy/kubernetes/scanrequest-local.example.yaml`. Query fixtures follow
`docs/query-api.md` and the Query API response DTOs. Never embed credentials,
source inventories, or real backend endpoints in interactive behavior.

Run `node --check site/main.js` and `mvn test` from the repository root.
The optional `scripts/test-site.mjs` browser check uses externally installed
Playwright; see its header for configuration. The site itself has no runtime
dependencies. The logo is the shared Astro Survey Atlas mark from Assets.
