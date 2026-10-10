# Integration candidate `integration/0.2.9-candidate`: build evidence

**Tested commit: `28acd193fc7c`** (third round). The evidence commit on top of it adds only files under this folder; no app, script or `onchain/` source changes after the tested commit. Earlier gates are kept in `gate-37f1e05a61fa/` (round 1) and `gate-077c76503d47/` (round 2).
**JVM, build and local-simulator results only.** This exact APK has not been installed on any phone or emulator.

## Merges (in order, off `origin/main` 2442ba1)

| # | Branch @ head | Merge commit | Conflicts |
|---|---|---|---|
| 1 | `fix/mwa-authorization` @ `5009e08` | `cf5419e` | none |
| 2 | `feature/storage-vault` @ `51a61cc` | `68ffe96` | `EdgeOreViewModel.kt` **import block only**: wallet `ConnectFailure`/`WalletConnection` vs storage `StorageController`/`StorageEvent`/`VaultFileView`. Kept both sets; no function body touched. |
| 3 | `feature/ai-edge-gallery` @ `6579e61` | `716ed10` | none (README and `known-limitations.md` auto-merged; both branches' lines kept) |
| 4 | `feature/anchor-receipt-settlement` @ `72647c4` | `2c644bd` | none (`onchain/` and its evidence only) |
| 5 | `feature/contribution-scheduler` @ `18484f4` (tested `daab56a`) | `b75dfa0` | none. `daab56a`'s `@ConscryptMode(OFF)` on `ContributionWorkTest` is kept; the full suite ran without the `TransportHardeningTest.nodeRedirectIsRefusedNotFollowed` hang. |
| 6 | `docs/ore-integration-design` @ `22370c5` | `7be3dbf` | none (one file, `docs/design/ore-integration-future.md`, marked DESIGN ONLY - NOT BUILT) |
| 7 | `feature/anchor-receipt-settlement` final head @ **`823e649`** (SBPF v3, claim deadline, verifier rotation, rent reclaim; tested `c2ba257`) | `3486dc7` | none (`onchain/` and `evidence/anchor-receipt-settlement/` only) |
| 8 | `origin/main` @ `52c8e5e` (README hero image) | `1de056c` | none. The image line lands above the tagline; the integration-candidate note and the verification-boundary wording are unchanged. |

## Suites (separate runs, never combined) — round 3, tested commit `28acd193fc7c`

| Suite | Command | Exit | Counts | Files |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean `28acd193fc7c` | **0** | Unit: **24 suites, 245 tests: 244 passed, 0 failed, 0 errors, 1 skipped** (`NodeAgentIntegrationTest`, needs a live agent). Lint debug: no issues. | `gate-28acd193fc7c/` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | **0** | **4 suites, 20 tests: 20 passed**, every shot at the 0.1% threshold | `screens-junit/`, `gate-28acd193fc7c/screens-verify.log` |
| Release-manifest check (standalone run) | `python3 scripts/check-manifest.py <merged release manifest>`; `python3 -m unittest scripts/test_check_manifest.py` | **0 / 0** | release manifest PASS; 9 tests: 9 passed (also inside qualify: EXIT=0) | `release-manifest/standalone-run.log` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **1 suite, 14 tests: 14 passed** | `receipt-checker/` |
| Anchor (`onchain/`) | not re-run in round 3 | — | `onchain/` tree is byte-identical to round 2 (`git rev-parse <c>:onchain` = `b30f882e…` at `077c765` and `28acd19`), so the round-2 results below still describe it | `anchor/` |

Before the recorded gate, a full `SCREENS=1 qualify.sh` on the intermediate `cf88c44` also exited 0 (not kept; superseded by the docs-only runbook commit). Two consecutive `verifyRoborazziDebug -Pscreens --rerun-tasks` runs after the re-record in `a973cf8` also passed.

## Suites — round 2, tested commit `077c76503d47` (kept for reference), tested commit `077c76503d47`

| Suite | Command | Exit | Counts | Files |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean `077c76503d47` | **0** | Unit: **24 suites, 241 tests: 240 passed, 0 failed, 0 errors, 1 skipped** (`NodeAgentIntegrationTest`, needs a live agent). Lint debug: no issues. | `gate-077c76503d47/` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | **0** | **4 suites, 19 tests: 19 passed** | `screens-junit/`, `gate-077c76503d47/screens-verify.log` |
| Release-manifest check (standalone run) | `python3 scripts/test_check_manifest.py`; `python3 scripts/check-manifest.py <merged release manifest>` | **0 / 0** | 9 tests: 9 passed; release manifest PASS (also run inside qualify: EXIT=0) | `release-manifest/standalone-run.log` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **1 suite, 11 tests: 11 passed** | `receipt-checker/` |
| Anchor build (`onchain/`, Agave 4.3.0 first on PATH for this run only) | `anchor build` (SBPF v3) | **0** | program `.so` sha256 `330b19b8…6235de`, 278,344 bytes (identical to the branch's recorded build) | `anchor/anchor-build.log` |
| Anchor LiteSVM | `anchor test --skip-build --skip-local-validator --skip-deploy` | **0** | **29 passed, 0 failed** | `anchor/anchor-test-litesvm.log` |
| Anchor local validator | `anchor test --skip-build --validator legacy --script validator --provider.wallet <throwaway keypair in a mktemp dir, deleted after>` | **0** | **28 passed, 0 failed** (local `solana-test-validator` only; the harness refuses non-local RPC URLs; nothing deployed to devnet) | `anchor/anchor-test-validator.log` |

Anchor toolchain: rustc 1.99.0, anchor-cli 1.2.1, solana-cli/cargo-build-sbf 4.3.0 (from `releases/4.3.0` placed first on `PATH`). The box's active Solana release was `releases/3.1.10` before and after the run (recorded in `anchor/commands-and-exit-codes.txt`). The anchor run was on merge `3486dc7`; `onchain/` is byte-identical at the tested commit (`git rev-parse <c>:onchain` = `b30f882e…` for both). The fresh clone has no committed program keypair, so `anchor build` printed *Program ID mismatch detected* (warning, exit 0).

Earlier gate attempts on intermediate commits (not candidate results):
- Round 1: `2c644bd` screenshot step failed on `ai` (fixed by `42420d5`); `8ca556d` release-manifest step failed on WorkManager components (rule refined in `37f1e05`).
- Round 2: `ea2ec1b` screenshot step failed on `FullScreenshotTest.storage` (0.109% pixels; the build machine's CPU and disk-rate lines both varied), kept in `evidence/build-ea2ec1b1ab05/`. Fixed by a per-shot threshold in `077c765` (test-only; see that commit).

## Screenshot baselines
See the re-record log in `docs/screenshots/README.md`. `06-review*` (stale since `cc31714`'s intentional expiry copy) came re-recorded from `feature/storage-vault` `8f9633d`; `02-ai*` re-recorded in `42420d5` for the AI branch's intentional section; `full/01-mine-full.png` came re-recorded from the scheduler branch (`cfed139`). Nothing else re-recorded in round 1. Round 2: `full/03-storage-full.png` in `3553c82` (new freshness line in the Resource telemetry card) and `02-ai.png` / `full/02-ai-full.png` in `5c891b6` (new execution title). Both are intentional and traced to those commits. Round 3: `03-storage.png` and `full/03-storage-full.png` in `a973cf8`, because renders now use fixed "not observed" device readings instead of the build machine's `/proc` counters; the per-shot 0.3% threshold is removed.

## Dependencies (`deps/COMPARISON.md`, generated in round 1; no `*.gradle.kts`, `gradle/` or `ondevice-llm/` change since, checked again at `28acd19`)
Resolved classpaths vs main 2442ba1, debug/release/unitTest/androidTest: **0 existing versions changed, 0 removed.** Added: `litertlm-android` 0.8.0, `gson` 2.13.2, `kotlin-reflect` 2.0.21 and `:ondevice-llm` (AI branch); `work-runtime(-ktx)` 2.10.0 with room 2.6.1, sqlite 2.4.0, lifecycle-livedata/service 2.8.7, tracing-ktx 1.2.0, concurrent-futures-ktx 1.1.0, kotlin-stdlib-jdk7/jdk8 1.8.22 (scheduler); `work-testing` 2.10.0 on the unit-test path only.

## Debug APK (round 3)
- `package: com.edgeore.app versionCode=11 versionName=0.2.9-review`
- sha256 `6d5eaf48e27883505a4dca2e9784d284d2876016fc01c571227688367fdf1761`, **57,286,793 bytes**
- `BuildConfig.GIT_COMMIT = 28acd193fc7c` (About text shows this stamp, no `-dirty`)
- Signer: Android **debug** key `c9b46665…7ae4b93`. No release signing.
- Box copies (not committed): `/workspace/EdgeORE-0.2.9-review-28acd193fc7c-debug.apk` and `/workspace/device-kit/` (with the mock wallet, `docs/DEVICE-RUNBOOK.md`, `APK-IDENTITY.txt` and `SHA256SUMS`). Round 2 APK (`077c76503d47`, sha256 `65efcd4f…29cf50b1`) and round 1 APK are superseded.

### ABI decision: unchanged, on purpose
LiteRT-LM 0.8.0 already ships only `arm64-v8a` (18,658,168 B) and `x86_64` (21,829,880 B) `liblitertlm_jni.so`, stored uncompressed. The only other native code is `libandroidx.graphics.path.so` (7–11 KB per ABI). So `abiFilters "arm64-v8a","x86_64"` would save about 16 KB and would make 32-bit devices unable to install the app at all (today they install, and a native-load failure is caught in `OnDeviceAiController` and shown as an error). Real savings (~19–22 MB per APK) need ABI splits or an App Bundle, which change the artifact from one `app-debug.apk` to several and would break `qualify.sh`'s APK identity step and the single-file Appetize upload. Left for a release-packaging change with its own review; the x86_64 emulator path is unaffected.
