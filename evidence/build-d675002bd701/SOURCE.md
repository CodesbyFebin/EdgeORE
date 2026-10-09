# Tested source for this evidence

- Tested commit: `d675002bd701` (full `d675002bd701f94809cf776aaa0580b3cb9e83ef`), the `main` merge of PR #3, app `0.2.8-review`, versionCode 10.
- How: fresh `git clone` of `https://github.com/CodesbyFebin/EdgeORE` at `main` = `d675002bd701`, clean tree. No app or tooling source was modified for this run.

## Gate A — Android (`scripts/qualify.sh`)

- Command: `NODE_IT=1 bash scripts/qualify.sh` (as it exists in `d675002bd701`).
- Exit: `qualify.sh` exit 0 (Gradle exit 0; `scripts/node-agent-it.sh` exit 0).
- Unit tests (JUnit XML in `junit/`, Android suite only): **164 tests, 163 passed, 0 failures, 0 errors, 1 skipped**
  (`NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent`, which needs a live agent; it ran and passed in `node-agent-it.log`: 1 test, 0 skipped).
- Lint: No issues found.
- Debug APK: sha256 `6ee17a3cac0c363b130caa2523118e068b3449f9f1d1ac49b449f3dae3f6a4e6`,
  `versionName='0.2.8-review'`, `versionCode='10'`, `BuildConfig.GIT_COMMIT = d675002bd701`,
  debug-signed (signer `c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93`).
  The APK itself is not committed.
- When: 2026-10-09 18:09:12–18:10:45 UTC (23:39:12–23:40:45 IST). Live node-agent observe at 2026-10-09 18:11:02 UTC (23:41:02 IST).

## Gate B — JVM receipt checker (`tools/receipt-checker`)

Recorded **separately** from Gate A. `qualify.sh` does not include these tests; do not sum the counts.

- Command: `bash gradlew -p tools/receipt-checker --no-daemon test installDist`
  (log: `receipt-checker.log`; JUnit XML: `receipt-checker-junit/`).
- Exit: 0.
- Tests: **7 tests, 7 passed, 0 failures, 0 errors, 0 skipped**.
- When: 2026-10-09 ~18:11 UTC (23:41 IST).

## Checker demo (synthetic export — not a real transfer)

Inputs under `receipt-checker-demo/` are a **generated test export**, not from a wallet,
devnet session, or installed APK journey.

- `receipt.json` — synthetic export → `scripts/verify-receipt.sh` → **VERIFIED: PASS**, exit 0.
- `receipt-tampered.json` — JSON-aware copy: one signed receipt body's integer `lamports`
  field incremented by 1; digest and signature left unchanged → **VERIFIED: FAIL**
  (`Receipt #2: SHA-256 does not match body (modified)`), exit 1.
- Original export bytes were not modified.

Claim: the checker verifies the integrity of **signed receipt contents**, not every field
in the export envelope.

## Scope of this evidence

JVM/build-box only. No phone, emulator, wallet, or real transfer is part of this gate.
No unpublished byte-mutation experiment is included here.

This evidence is committed in a **later** commit on branch `evidence/d675002`. That commit
adds only these files under `evidence/build-d675002bd701/`; they describe `d675002bd701`
and nothing after it. Evidence-only: no re-gate of the app is required for merge.
