# EdgeORE submission manifest — 0.2.8-review

Status key: **PRESENT** = artifact exists and is linked; **NOT_RUN** = not yet performed, no artifact exists.
Nothing below marked NOT_RUN may be described as done. Fill a row only with the real artifact from the operator session.

## 1. Tested source

| Field | Value |
|---|---|
| Tested source commit | `d675002bd701f94809cf776aaa0580b3cb9e83ef` (merge of PR #3) |
| Evidence commit on `main` | `111a3dba48c3` — evidence-only merge of PR #4 (adds `evidence/build-d675002bd701/`; no app or tooling change) |
| Provenance note | [`build-d675002bd701/SOURCE.md`](build-d675002bd701/SOURCE.md) — fresh clone at `d675002bd701`, clean tree |

## 2. APK identity

| Field | Value |
|---|---|
| File | `EdgeORE-0.2.8-review-d675002bd701-debug.apk` (not committed; attached to the draft GitHub Release `0.2.8-review`) |
| SHA-256 | `6ee17a3cac0c363b130caa2523118e068b3449f9f1d1ac49b449f3dae3f6a4e6` |
| Size | 15,080,965 bytes |
| Package / version | `com.edgeore.app` · `0.2.8-review` · versionCode `10` |
| SDK | minSdk 26 · targetSdk 35 |
| BuildConfig.GIT_COMMIT | `d675002bd701` |
| Signing | APK Signature Scheme v2, `CN=Android Debug`, cert SHA-256 `c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93` |
| Build type | debug (`debuggable=true`), Solana **devnet** only |
| Checksums file | [`../release/checksums.txt`](../release/checksums.txt) |

## 3. Execution environment (build box)

| Field | Value |
|---|---|
| Host | Linux 6.12 x86_64 (Debian 13) build machine — **no phone, emulator, or wallet** |
| Toolchain | OpenJDK 17.0.20.1, Gradle 8.9 wrapper, Android SDK 35 |
| Node agent | `deproof-node` built from `CodesbyFebin/DeProof--EdgeORE` (MIT upstream) at `7431f0896f4f…` per `scripts/node-agent.pin` |
| Gate time | 2026-10-09 18:09:12–18:10:45 UTC (23:39–23:40 IST) |
| Emulator | **NOT_RUN** — host KVM fault (`kernel BUG at arch/x86/kvm/x86.c:702`); software-only boot did not complete in ~9 min. Diagnostics kept on the build box (not committed). |

## 4. Gate evidence (PRESENT)

Counts are from separate suites. **Do not sum them.**

| Gate | Result (observed) | Evidence |
|---|---|---|
| A — Android suite (`NODE_IT=1 bash scripts/qualify.sh`) | 164 tests · 163 passed · 0 failed · 0 errors · 1 skipped; Gradle exit 0 | [`summary.md`](build-d675002bd701/summary.md), [`junit/`](build-d675002bd701/junit/), [`build.log`](build-d675002bd701/build.log) |
| A — skipped test run live | `NodeAgentIntegrationTest` pair → observe → revoke → refused-after-revoke PASSED against `deproof-node`; `node-agent-it.sh` exit 0 | [`node-agent-it.log`](build-d675002bd701/node-agent-it.log) |
| A — Lint (debug) | No issues found | [`lint-debug.txt`](build-d675002bd701/lint-debug.txt) |
| B — JVM receipt checker (`tools/receipt-checker`) | 7 tests · 7 passed · 0 failed · 0 skipped; exit 0 | [`receipt-checker.log`](build-d675002bd701/receipt-checker.log), [`receipt-checker-junit/`](build-d675002bd701/receipt-checker-junit/) |
| Checker demo (**synthetic** export) | original → VERIFIED: PASS, exit 0; copy with one signed body's `lamports` +1 → VERIFIED: FAIL, exit 1 | [`receipt-checker-demo/`](build-d675002bd701/receipt-checker-demo/) (`exits.txt`, `original.out`, `tampered.out`) |

`qualify.sh` and checker exit codes are recorded in the prose of `SOURCE.md` / `summary.md`, not as separate exit files.
Checker claim: it verifies the integrity of **signed receipt contents**, not every field of the export (see `docs/hardening-backlog.md` H4).

## 5. Runtime submission artifacts (operator session)

| Artifact | Status | Value |
|---|---|---|
| Wallet authorization (MWA) on Android | **NOT_RUN** | — |
| Devnet transfer — operation ID | **NOT_RUN** | — |
| Devnet transfer — transaction signature | **NOT_RUN** | — |
| RPC confirmation response (`getSignatureStatuses`) | **NOT_RUN** | — |
| Solana Explorer URL (devnet) | **NOT_RUN** | — |
| Restart-recovery observation | **NOT_RUN** | — |
| Device screenshots (installed app) | **NOT_RUN** | — (the images in `docs/screenshots/` are rendered UI tests, not device captures) |
| Real-transfer receipt export | **NOT_RUN** | — |
| Checker logs on real receipt (original PASS / signed-content tamper FAIL) | **NOT_RUN** | — |
| Demo video | **NOT_RUN** | — |
| Pitch deck link | **NOT_RUN** (deck drafted locally; not published) | — |
| GitHub Release `0.2.8-review` | Draft only, not published | — |

## Not cited

- The earlier unpublished single-byte mutation experiment (no preserved script/output) is excluded.
- APKs from other builds (e.g. `cde5537` builds) are not this candidate.
