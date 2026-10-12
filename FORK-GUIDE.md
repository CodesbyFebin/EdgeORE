# Build your own evidence-first edge workspace

EdgeORE's useful extension points are BackupProvider, durable operation controllers, the existing
signed receipt verifier and the optional offline consent/evidence kit. Start with one provider or
one bounded workload rather than copying every dependency into the Android app.

## Quick local checks

Requires Python 3.11+; Android separately requires the pinned Gradle wrapper, JDK 17 and SDK 35.

    python3 scripts/build-policy.py
    python3 scripts/test_build_policy.py -v
    python3 -m unittest discover -s tools/edgeore-kit -v
    python3 -m unittest discover -s services/storage-gateway -v

For Android, run bash scripts/qualify.sh on a network-enabled clean clone. Historical logs are not
your test results. Keep checker, screenshots, Android, backend and settlement counts separate.

Read docs/extensions/RESEARCH-AND-ROADMAP.md, docs/storage/INTEGRATION.md and docs/build/ADAPTATION.md.
The gateway needs a private external token and an owner-managed HTTPS reverse proxy. Never commit
that token or embed it into BuildConfig. Default Android remote storage remains disabled.

## First contributions

- Add adversarial tests for the optional Kotlin gateway adapter and qualify it against HTTPS.
- Add a documented provider adapter with opaque references and bounded operations.
- Integrate explicit capability/consent UI without treating configuration as observed availability.
- Add a real second-device restore test after a reviewed portable envelope exists.
- Improve accessibility and setup instructions with evidence on the tested build.

Use the extension proposal template. A patch must describe data destination, consent, bounds,
cancellation, persistence, cryptographic assurance and actual execution environment.

## Licensing and credits

This checkout has no owner-chosen top-level LICENSE. Do not advertise it as MIT or as freely
redistributable until that decision is made. Preserve all existing third-party notices. An upstream
repository being public is not permission to copy its code. Source references in the roadmap are
architecture credits, not bundled dependencies. RunAnywhere and CrossPaste have different license
terms and were not imported.

## Discoverability that earns trust

Publish a short real demo, a supported-device table, exact installable artifact identity and one
reproducible extension example. Link documentation from README. Describe implemented versus planned
features explicitly. Helpful issues and a small working adapter are more useful than inflated badges,
repo-count claims, star requests or generated testimonials. Do not label unrun CI green.
