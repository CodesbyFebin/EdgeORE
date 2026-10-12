# Consolidated submission hardening

Candidate: PR #27, `submission/harden-and-merge`. Integrates PR #20, #22, #23, #24, #25 and #26, preserving the current ORE-first UI and the concrete private disclosure policy.

## Changes

- Refuse malformed negative balances without subtraction overflow in ORE MAX; refusal has no transaction bytes.
- Preserve the 10,000,000-lamport mainnet deployed-amount cap, wallet signing for every deploy, separate submission and observation-only recovery. Rent and network fees remain additional.
- Fail closed on incomplete MobSF scan evidence. Scan a release-shaped, unminified variant with a disposable CI signature, not an owner release key. Production release builds retain R8 and private signing configuration.
- Retain raw scanner findings; only loopback-only traffic and the AndroidX biometric platform helper receive bounded, tested triage. Unknown high findings block. MobSF image is pinned to the observed v4.3.0 digest.
- Refresh native screenshot baselines from CI-generated app renders only after inspection; verification remains separate. These are JVM images, not device evidence.
- Remove duplicate receipt-checker instructions and label historical counts as historical.

## Fresh local checks

Executed in the Linux Codex workspace with Python 3.11+, JDK 17. Logs in this directory. Policy tests, gateway (17), offline kit (22), manifest tests (9) and scanner policy tests (5) passed. Shell syntax and `git diff --check` passed. The Android Gradle bootstrap failed because services.gradle.org is network-blocked, so Android verification runs on GitHub Actions against the candidate.

## Limits

No wallet signature, mainnet transaction, program deployment, registry publication or store publication was performed. Device MWA signing, real mainnet landing, interrupted-submit device recovery and final production artifact qualification remain NOT_RUN. A source merge does not establish ORE or SKR prize eligibility. The generated narrated presentation is not a device-recorded demo.

CI identities, final test counts, reviewed screenshot provenance and APK checksum will be recorded here after the exact candidate completes.
