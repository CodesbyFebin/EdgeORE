# Unified upgrade evidence (EdgeORE build host)

Produced on EdgeORE's network-enabled build host for commit `8cf386c` on `feature/unified-upgrade` (base `main`
`268fd74`). JVM, Gradle and Python results only: nothing ran on a phone or an emulator, and the GitHub workflow has
not run. Numbers are in `STATUS.json`; the full `SCREENS=1 scripts/qualify.sh` evidence is in
`evidence/build-8cf386c9c9df/`. The patch author's own logs are kept, labelled, in each `author-environment/` folder
under `evidence/storage-gateway-adaptation/`, `evidence/build-governance/` and `evidence/extension-kit/`.

These are separate suites; never add their counts together:

| Suite | Result | Log |
|---|---|---|
| edgeore-kit | 22/22 | `kit-tests.log` |
| storage-gateway | 13/13 | `gateway-tests.log` |
| build policy | PASS | `policy.log` |
| build-policy tests | 12/12 | `policy-tests.log` |
| Android unit (qualify.sh) | 265: 264 passed, 1 skipped, 0 failed; lint clean | `../build-8cf386c9c9df/summary.md` |
| Screenshots (verifyRoborazziDebug) | 25/25, baselines unchanged | `verify-roborazzi.log`, `screens-junit.txt` |
| Receipt checker | 14/14, installDist OK | `receipt-checker.log`, `receipt-checker-junit.txt` |
| Release manifest | PASS | `release-manifest.log` |

Resolved graphs (`deps/`, `deps/DIFF.txt`): all four runtime-classpath reports (app and ondevice-llm, release and debug)
are byte-identical to main, so the series adds no dependency. `app debugCompileClasspath` lists two compose-bom
constraint lines in a different order (same versions). LiteRT-LM stays off the app compile classpaths.
`apk-compare.txt` has the APK hash and the comparison with main's APK.
