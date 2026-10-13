# Website upgrade verification

13 October 2026. Base source 541c05c; exact tested website file hashes in STATUS.json.
These checks apply to the static website working tree, not a new APK or mainnet transaction.

- python3 scripts/check-site.py: exit 0. Canonical, metadata, JSON-LD/visible FAQ consistency,
  sitemap, headings, local assets and alias noindex/canonical checks.
- actionlint 1.7.7 .github/workflows/site-pages.yml: exit 0.
- Playwright with installed /usr/bin/chromium, axe-core: exit 0 at widths 390/768/1440.
  No overflow, broken images, console errors, external requests or axe WCAG A/AA violations.
  FAQ opens using native details/summary. Screenshots reviewed. Automated accessibility
  checks do not replace screen-reader/manual testing. Browser results are in browser-qa.json.
- Local mobile Lighthouse: performance 100, accessibility 100, best practices 100, SEO 100.
  Full output lighthouse-local.json records tool version, configuration, metrics and remaining
  diagnostic opportunities. A 100 category score does not mean every advisory audit passed.
- Command: CHROME_PATH=/usr/bin/chromium lighthouse http://127.0.0.1:8765/
  --chrome-flags='--headless --no-sandbox' --only-categories=performance,accessibility,best-practices,seo
  --output=json --output-path=site-lighthouse.json --quiet; exit 0.
- Server: python3 -m http.server 8765 --directory docs/site.
- Production URL request returned HTTP 403 under the environment's outbound host restrictions.
  This is not evidence the public site itself returns 403. Live-site inspection/deployment
  confirmation: NOT_RUN. Search Console, indexing, rankings, AEO/GEO citations: NOT_MEASURED.
- No financial actions, wallet prompts, app publishing or NFT minting occurred.

Reproduce browser checks with Playwright and axe-core installed in your tooling environment;
run browser-qa.cjs while the local server is active. Those tools are not website dependencies.
