# Hardening backlog (post-submission)

Source: static review of the tested APK `EdgeORE-0.2.8-review-d675002bd701-debug.apk`
(sha256 `6ee17a3c…f3dae3f6a4e6`) with `aapt2 dump badging`, `aapt2 dump xmltree AndroidManifest.xml`
and `apksigner verify --print-certs` (build-tools 35.0.0), plus `app/src/main/java/com/edgeore/app/receipts/Receipts.kt`
at `d675002bd701`. These are known properties of the **debug review candidate**; none is fixed in `0.2.8-review`.

| # | Item | Observed | Planned action |
|---|---|---|---|
| H1 | `android:debuggable=true` | Manifest `application-debuggable`; expected for a `debug` build type. | Ship submissions/production from a `release` build type (debuggable false, R8/minify reviewed). |
| H2 | Exported debug/test activities in the debug manifest | `androidx.compose.ui.tooling.PreviewActivity` (exported=true), `androidx.activity.ComponentActivity` (exported=true), and three `androidx.test.core.app.InstrumentationActivityInvoker` activities — `$BootstrapActivity`, `$EmptyActivity`, `$EmptyFloatingActivity` (exported=true, LAUNCHER category). Merged in from `debugImplementation` tooling/test dependencies. | Confirm they are absent from the release manifest; keep `ui-tooling` / `androidx.test` on `debugImplementation`/`androidTestImplementation` only; add a manifest check to the gate. **Release half done on `integration/0.2.9-candidate`:** the merged *release* manifest also carried the three exported `InstrumentationActivityInvoker` activities (MWA clientlib-ktx 2.0.3 puts `androidx.test.ext:junit-ktx` on the runtime path). `app/src/release/AndroidManifest.xml` removes them, and `scripts/check-manifest.py` (tests: `scripts/test_check_manifest.py`) now fails `qualify.sh` if the release manifest is debuggable or exports anything except `MainActivity` or a component guarded by a system-only permission (`BIND_JOB_SERVICE` for WorkManager's `SystemJobService`, `DUMP` for the profile-installer and WorkManager diagnostics receivers). The debug manifest still has them (expected for a debug build). No signed release APK exists yet. |
| H3 | Release signing with the owner's key | APK is v2-signed by `CN=Android Debug` (cert sha256 `c9b46665…7ae4b93`). A debug-signed install cannot be updated by a differently signed APK. | Owner creates and holds a release key (not in the repo), configures signing outside source control, records the release cert SHA-256 in `release/checksums.txt`, and documents the migration path for debug installs (vault data is device-bound). |
| H4 | Unsigned descriptive export fields | In `ReceiptLog.export`, top-level `note`, `exclusions`, `payment`, `location`, `exportedAt`, and the key-epoch labels (`protection`, `firstUsedAt` in `deviceKeys`) are written into the export but are **not covered by any signature**. Receipt bodies are digest- and device-signed; FULL_CHAIN exports additionally sign a checkpoint (count, sequence range, last digest, bundle digest, checkpoint `exportedAt`). | Either sign an envelope over these fields or have the verifier/UI label them explicitly as unsigned descriptive text. Until then, the checker claim stays: it verifies the integrity of **signed receipt contents**, not the whole export. **Done on `integration/0.2.9-candidate`:** exports now carry an optional signed `envelope` (domain `edgeore.export-envelope.v1`, current device key) over `exportedAt`, `note`, `payment`, `location`, each exclusion, each damaged line and each key epoch's `protection`/`firstUsedAt`. The export schema, receipt lines and checkpoint are unchanged, and older checkers ignore the field. A present envelope must verify (`ReceiptV2Test.editingAnyDescriptiveFieldBreaksTheEnvelope`, `ReceiptCheckerTest.signedEnvelopeDetectsEditedDescriptiveFields`). Every new export also writes a top-level `envelopeRequired` flag (`true` with an envelope; `false` when the device-key privacy control is on, since nothing can sign it). Round 3 rule: `envelopeRequired: true` with the envelope missing is **rejected** (`Envelope: required by this export but missing (removed after export)`); a non-boolean flag or a non-object envelope is rejected. An export with neither field is a legacy (pre-envelope) export: it still verifies its receipts and is labelled *descriptive fields not signed (legacy export without envelope)*; the committed export from build `d675002bd701` verifies this way (`ReceiptCheckerTest.realPreEnvelopeExportFromBuildD675002StillVerifies`). Remaining limit, stated plainly: the flag itself is not signed (signing it would change the checkpoint format older checkers rely on), so a holder who removes **both** the envelope and the flag gets the legacy label, never a signed result. |
| H5 | AI tab copy: "On-device execution" | Observed on Appetize (API 33) against the `d675002bd701` APK: the Private AI tab shows the label **On-device execution**, although this build talks to an owned Ollama-compatible host and has no embedded AI runtime. See [`evidence/appetize-d675002bd701/SESSION.md`](../evidence/appetize-d675002bd701/SESSION.md). | Post-submission UI copy fix so the label matches the owned-host architecture; do not treat the current label as an on-device inference claim. |

Not in this list: the runtime qualification items (wallet, devnet transfer, restart recovery, device Keystore, video). Those are tracked as NOT_RUN in `evidence/SUBMISSION-MANIFEST.md` and `docs/qualification-status.md`.

## Added for the dApp Store launch package (2026-10-12, `84a788cbd926`)

| # | Item | Status | Planned action |
|---|---|---|---|
| H6 | Detekt | DESIGN | Add the Gradle plugin at a pinned version via the version catalog; baseline current findings; fail CI on new ones. |
| H7 | ktlint | DESIGN | Pinned ktlint check task in CI; format-only commits kept separate from logic. |
| H8 | MobSF static scan | NOT_RUN | Scan the release APK; record report hash under `evidence/`; triage findings here. |
| H9 | Release signing | BLOCKED (owner key) | See H3. Key held outside the repo; record cert SHA-256 only. |
| H10 | bundletool | NOT_RUN | Build an AAB, `bundletool build-apks` + `validate`, record outputs and sizes. |
| H11 | Solana dApp Store flow | NOT_RUN | Publisher/app NFT mint via the dApp Store publishing CLI, listing copy matching the pitch labels, privacy policy, store screenshots. |
| H12 | Store declarations | DESIGN | Declare `CAMERA` (QR entry, optional), `INTERNET`; no analytics/tracking SDKs; data-safety text consistent with on-device vault. |
| H13 | LICENSE | Missing | Repo has `NOTICE` only; owner chooses a licence before listing. |
| D1 | BLE, mesh, WebRTC, libp2p transports | Deferred | Not in scope until device gates pass. |

## Toolchain backlog (2026-10-12; not added to the build yet)

Build stack today (`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`): Gradle 8.9, AGP 8.7.3,
Kotlin 2.0.21, Compose BOM 2024.12.01, MWA `mobile-wallet-adapter-clientlib-ktx` 2.0.3. Nothing below is wired; each item
needs its own branch, a pinned version in the catalog, the build policy updated and the gates re-run.

| # | Item | Compatibility / status | Planned action |
|---|---|---|---|
| T1 | Detekt | detekt 1.23.8 is built against Kotlin 2.0.21; its compatibility table lists Gradle 8.12.1 / AGP 8.8.1 as tested and the plugin needs Gradle ≥ 6.8.3 ([detekt.dev compatibility](https://detekt.dev/docs/introduction/compatibility/)). Expected to work on Gradle 8.9 / AGP 8.7.3, **not verified here**. detekt 2.0 needs Gradle ≥ 8.14.3, so it is out until a Gradle upgrade. | Add `io.gitlab.arturbosch.detekt` 1.23.8 with a committed baseline (no new findings gate), report its findings separately. |
| T2 | ktlint | `org.jlleitschuh.gradle.ktlint` 12.1.2 needs Gradle ≥ 7.4 and AGP ≥ 4.1.0 ([ktlint-gradle](https://github.com/jlleitschuh/ktlint-gradle)); the ktlint engine version must be pinned (the plugin's default can change between patches). Alternatively detekt-formatting 1.23.8 wraps ktlint. **Not verified on this build.** | Choose one formatter path, pin it, run it once as report-only, then gate. |
| T3 | MobSF | Static scan of the release APK; not run. | Run MobSF as a pre-release scan of the signed release artifact; record its version, the APK hash and the report; triage findings into this file. Not a CI dependency. |
| T4 | Release signing and bundletool | No release key or signed release exists (H3). | Owner-held key outside the repo; signed `bundleRelease`; `bundletool build-apks` / `get-size total` and an install test from the generated APK set; record hashes in `release/checksums.txt`. |
| T5 | Google Play declarations | Not filed. EdgeORE has wallet review/sign/submit (devnet) and a WorkManager scheduler. | Before any Play listing: the financial-features declaration (describe the devnet-only wallet review, no custody, no lending or trading), and, only if a foreground service is ever added, its FGS type and justification (none is declared today; verify in the merged manifest first). |
| T6 | Solana dApp Store | Not published. | Follow the dApp Store publisher flow (publisher NFT, app and release NFTs via the publishing CLI, signed release APK, policy review) once T3–T4 are done and the device gates have evidence. Owner authorization required. |

Deferred (no design or build planned in this release): BLE transport, Meshrabiya mesh, WebRTC and libp2p peer links. They
would add new network surfaces and consent questions; revisit only with a threat model and a qualified use case.
