# EdgeORE website

Buildless showcase at canonical URL https://codesbyfebin.github.io/EdgeORE/.
Source: index.html, style.css and assets/. Images are copied from the repository's current
JVM-render screenshots, not physical-device captures. All claims are scoped to source and
recorded qualification; no wallet, transaction, analytics or external script runs here.

## Publish

The EdgeORE website workflow validates pull requests and publishes docs/site on relevant
main pushes. GitHub Settings → Pages → Source must be GitHub Actions. It does not change
repository settings or publish an APK, NFT or dApp Store release. The deploy job alone has
pages:write/id-token:write. All actions are pinned to full commit IDs.

Root, docs-root and older judges entry points redirect to the showcase for legacy
branch-based Pages setups. They use noindex and the primary canonical URL. With the
Actions artifact, index.html is served directly at /EdgeORE/ and assets at /EdgeORE/assets/.
Do not publish both copies as separate indexed pages.

## Search and AI discovery

One descriptive H1, visible FAQ answers, WebSite/WebPage/FAQPage/SoftwareApplication/
SoftwareSourceCode/Person JSON-LD, matching entity identifiers, absolute canonical and
Open Graph/Twitter metadata. No invented reviews, ratings, earnings or offers. Only the
canonical home URL is included in sitemap.xml. FAQ markup does not guarantee rich results.
llms.txt is supplemental machine-readable guidance, not a ranking standard.

On GitHub Pages project sites, /EdgeORE/robots.txt is NOT the authoritative robots file:
crawlers consult https://codesbyfebin.github.io/robots.txt. If this account's root site is
managed separately, add a Sitemap directive there pointing to this project's sitemap.
Canonical hints, redirects and sitemap avoid competing URLs; they cannot guarantee link
signal consolidation, rankings, AI citations or a universal SEO/AEO/GEO score.

## Check

    python3 scripts/check-site.py
    python3 -m http.server 8765 --directory docs/site

Browser and Lighthouse evidence is recorded separately under evidence/site-upgrade/.
Local scores are not deployed results. After deployment, verify HTTP 200, asset URLs,
canonical, sitemap, sharing image, host-root robots, and Search Console indexing. No
claim of live validation is made while the production host is blocked by build-host policy.
