# Tested source for this evidence

- Tested commit: `cde5537df660` (full `cde5537df6603267e948ca9121e3b9b427bb1c2a`), the `main` merge of PR #2, app `0.2.8-review`, versionCode 10.
- How: fresh `git clone` of `https://github.com/CodesbyFebin/EdgeORE` at `main` = `cde5537`, clean tree, then
  `NODE_IT=1 bash scripts/qualify.sh` (the `qualify.sh` as it exists in `cde5537`).
- Exit: `qualify.sh` exit 0 (Gradle exit 0, `node-agent-it.sh` exit 0).
- Unit tests (JUnit XML in `junit/`): 164 tests, 163 passed, 0 failures, 0 errors, 1 skipped
  (`NodeAgentIntegrationTest`, which needs a live agent; it ran and passed in `node-agent-it.log`: 1 test, 0 skipped).
- Lint: No issues found.
- Debug APK: sha256 `46b443f7d4b1a04e1b832d442fda8a967dd57ed6a3bcba1fe37eb57e9299407a`, `BuildConfig.GIT_COMMIT = cde5537df660`, debug-signed. The APK itself is not committed.
- When: 2026-10-09 17:36–17:38 UTC (23:06–23:08 IST).

This evidence is committed in a **later** commit on branch `tools/verify-receipt`. That commit adds only these
files; they describe `cde5537df660` and nothing after it.

JVM/build results only. No phone, emulator, wallet, devnet transaction or on-device node session is part of this gate.
