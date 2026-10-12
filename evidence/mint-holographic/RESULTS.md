# Mint holographic refinement — recorded gates

Tested implementation: `164f04e4b846c158d2cd782c5f235c218aefa0d8`. Original workspace base: `260222e318d8a1deccff15abcdbdbe11ca4e11e4`; current main `bca02efea46a19b928c127eb13fcbd7156a095a4` was merged before this final gate. Linux build host, JDK 17 / Android SDK 35 / Gradle 8.9. Gate logs record their start/end and commands. Evidence recorded 12 October 2026 (IST); machine timestamps in logs are UTC.

The APK stamp is `164f04e4b846-dirty`: the tracked screenshot baselines and their README were intentionally being refreshed; no uncommitted production-source changes existed at the final gate. Later evidence/documentation commits do not imply a new tested source result.

| Gate | Recorded result | Evidence |
|---|---|---|
| Android JVM regression suite | 336 tests; 331 passed; 0 failures/errors; 5 skipped | `android-junit/`, `android-gate.log` |
| Debug lint | No issues found | `lint.txt` |
| Debug APK + instrumented-test APK assembly | Build successful, Gradle exit 0 | `android-gate.log`, `APK-IDENTITY.txt` |
| Screenshot/interaction record | 29 passed, exit 0 | `screens-record.log`, `screens-junit/` |
| Screenshot comparison after record | 29 passed, exit 0 | `screens-verify.log` |
| Standalone receipt checker (fresh rerun) | 14 passed, exit 0 | `receipt-checker.log`, `receipt-checker-junit/` |
| Build declaration policy | PASS | `build-policy.log` |
| Build-policy tests | 12 passed | `build-policy-tests.log` |
| Release-manifest self-tests | 9 passed | `manifest-tests.log` |
| Release-manifest inspection | PASS | `manifest-check.log` |
| Palette contrast calculations | Selected primary/supporting/action/disabled pairs exceed 4.5:1 | `contrast.txt` (not a full device contrast audit) |
| Attached Android runtime | None | `android-runtime.txt` |
| Public Solana RPC access | BLOCKED: CONNECT forbidden (403) | `rpc-access.txt` |
| Device MWA, real balances, recovery, notifications, TalkBack, real provider session | NOT_RUN | No runtime evidence claimed |

APK SHA-256: `1b832497f3454b00b75ed290187a208b5984d3f9fc738e8dcf688d13429992a8`. Package `com.edgeore.app`, `0.1.0-clockin`, versionCode 12, debug-signed review candidate. This APK is not a signed production release or store publication.

Skipped Android cases require a live node/gateway/proxy or optional preview capture: ContributionSessionE2ETest, GatewayProxyIntegrationTest, NodeAgentIntegrationTest, MiningAnimationComposeTest.frames and OrePreviewFrames.frames. They are not included as passes.

The 200% text interaction now scrolls horizontally to Receipts and opens it. Sheet/dialog snapshots choose their own window root. Settings hides the varying receipt key identity behind **Signer identity**; the identity remains accessible. Help-clock and unavailable-telemetry seams are confined to tests. No mock balances or successful reward handlers were introduced.

Open engineering/configuration work remains in `docs/design/mint-implementation-report.md`. In particular, SKR registry, mainnet/testnet transactions, ORE deployment, funded check-in rewards, server eligibility/leases, bandwidth/cloud providers and push inbox are not implemented by these gates.

## Judge Project #3

Prepared judge README, field/view mapping, nine cards and apply script are in `docs/judges/project-3/`. Public title/readme were inspected. The apply attempt failed before mutations at the owner/project GraphQL preflight: Forbidden, exit 1 (`project-edit-attempt.txt`). The live Project remains unchanged. Script syntax and its local plan were checked; this is not a live API pass. Repository source and judge package are proposed in draft PR #20.

Final integrated candidate retains upstream current-main changes. Settings screenshot build identity is explicitly labelled **JVM render** through a test-only display seam, so CI comparisons do not depend on a changing git stamp. Runtime uses the actual BuildConfig stamp. New Android/screenshot gates were rerun after integration; the verifier source did not change, and its recorded 14-test rerun remains separately scoped.
