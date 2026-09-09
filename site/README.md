# Warehouse Documentation Site

Open `index.html` directly in a browser. No build, server, external fonts,
CDN, or API connection is required. Repository links need internet access;
query responses are illustrative offline fixtures, not live survey results.
Clipboard access depends on browser permissions; text remains selectable.

## GitHub Pages

In repository Settings > Pages, choose **GitHub Actions** as the source.
The `Documentation Pages` workflow publishes only `site/` on matching pushes
to `main`, or on manual dispatch. The expected project URL is
<https://Astro-Survey-Atlas.github.io/Astro-Survey-Atlas-Warehouse/>.
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
