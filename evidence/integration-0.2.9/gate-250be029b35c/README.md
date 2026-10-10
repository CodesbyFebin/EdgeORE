# Final integration gate — tested commit `250be029b35c`

`250be029b35c` = `integration/0.2.9-candidate` after merging `feature/settlement-spec-v2` @ `305f1e7` (merge, no conflicts) on top of the README commit `35ff869` (device-bound vault line). The evidence commit on top adds only files under this folder.
**JVM, build and local-simulator results only. No phone, emulator, wallet or devnet run is part of this gate.** Each suite was run separately; the counts are never combined.

| Suite | Command | Exit | Counts | Files |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean `250be029b35c` | **0** | Unit: **24 suites, 245 tests: 244 passed, 0 failed, 0 errors, 1 skipped** (`NodeAgentIntegrationTest`, needs a live agent). Lint debug: no issues. | `summary.md`, `build.log`, `junit/`, `lint-debug.txt` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | **0** | **4 suites, 20 tests: 20 passed** (Roborazzi summary: 18 compared images, 18 unchanged) | `screens-junit/`, `screens-verify.log` |
| Release-manifest check (standalone) | `python3 -m unittest scripts/test_check_manifest.py`; `python3 scripts/check-manifest.py <merged release manifest>` | **0** | 9 tests: 9 passed; MANIFEST CHECK: PASS (also inside qualify: EXIT=0) | `release-manifest/standalone-run.log`, `release-manifest.log` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **1 suite, 14 tests: 14 passed** | `receipt-checker/` |
| Anchor build (`onchain/`, Agave 4.3.0 first on PATH for this run only) | `anchor build` (SBPF v3) | **0** | `receipt_settlement.so` sha256 `9ce9addf14b1b432305bbcdfaabe25c174b32f00f27e18534e0b996009bf78a7`, 274,352 bytes (identical to the branch evidence for `047657f89b48`) | `anchor/anchor-build.log` |
| Anchor LiteSVM | `anchor test --skip-build --skip-local-validator --skip-deploy` | **0** | **25 passed, 0 failed** | `anchor/anchor-test-litesvm.log` |
| Anchor local validator | `anchor test --skip-build --validator legacy --script validator --provider.wallet <throwaway keypair in a mktemp dir, deleted after>` | **0** | **25 passed, 0 failed** (local `solana-test-validator` 4.3.0 only; nothing deployed to devnet) | `anchor/anchor-test-validator.log`, `anchor/commands-and-exit-codes.txt` |

The box's active Solana release was `releases/3.1.10` before and after the anchor run.

## Debug APK
- `package: com.edgeore.app versionCode=11 versionName=0.2.9-review`
- sha256 `c547d7dfe5402d4f390a87571d9d31028506b11194742fd1c154b2b842a88d5b`, **57,286,793 bytes**
- `BuildConfig.GIT_COMMIT = 250be029b35c` (About shows `EdgeORE 0.2.9-review (250be029b35c)`; no `-dirty`)
- Signer: Android **debug** key `c9b46665…b7ae4b93`. No release signing.
- This APK has **not** run on any device or emulator. Runtime gates in `docs/DEVICE-RUNBOOK.md` stay NOT_RUN.
