# Screenshots (generated, not hand-made)

These images are rendered from this repository's source by `app/src/test/java/com/edgeore/app/screens/ScreenshotTest.kt`
(Robolectric 4.14.1 + Roborazzi 1.36.0, API 28, Pixel 7 qualifiers; `full/` uses a 411×4200 dp viewport so every control is visible).

```
source /path/to/android-env   # JDK 17 + Android SDK 35
./gradlew --no-daemon :app:recordRoborazziDebug -Pscreens   # rewrite these files
./gradlew --no-daemon :app:verifyRoborazziDebug -Pscreens   # fail if the UI no longer matches
```

The suite runs only with `-Pscreens`; the normal `testDebugUnitTest` excludes it.

What these images are and are not:
- They show the app in its **fresh-install state** on the JVM: no wallet, no node, no AI host, no receipts.
- On API 28 the Android thermal API does not exist, so thermal status renders as *not observed*; battery is *not observed*.
- The CPU percentage on the Storage page comes from the **build machine's** `/proc/stat` through Robolectric, not from a phone.
- They are **not device screenshots**. Phone qualification is still NOT_RUN (see `docs/known-limitations.md`).

## `storage/` — Storage page JVM renders (feature/storage-vault)

`StorageRenderTest` renders only the Storage vault section. Labelled `jvm-render-*` because they are **JVM renders, not device screenshots**:
the two listed files are real EOV2 objects encrypted into a temporary vault by a **software** AES key on the build machine
(Robolectric has no Android Keystore), remote backup is the shipped `NotConfiguredBackupProvider`, and device free space is not
observable in that harness, so it reads *Not observed*. In the full-app renders (`03-storage*.png`) Robolectric's own storage stub reports 0 B.

## Re-record log (integration/0.2.9-candidate)

Baselines are re-recorded only when the screen change is intentional and traced to a source commit:

| Baseline | Why it changed | Source change |
|---|---|---|
| `06-review.png`, `full/06-review-full.png` | Review copy for the expiry policy changed on `main` without a re-record, so `verifyRoborazziDebug` failed on `main` itself. Re-recorded in `8f9633d` (from `feature/storage-vault`). | `cc31714` `ReviewScreen.kt` (expiry-policy wording) |
| `02-ai.png`, `full/02-ai-full.png` | The AI page gained the on-device LiteRT-LM section (allowlist, download state); `feature/ai-edge-gallery` did not run the screenshot suite. | `4f665e5` `AiScreen.kt` |

All other baselines verified unchanged after the merges. A record run also rewrote `full/03-storage-full.png` with a sub-threshold difference; that file was restored because `verifyRoborazziDebug` already passes against the committed baseline.

### Re-record: build-machine-independent readings (integration/0.2.9-candidate, round 3)

The screenshot suite now installs `FixedDeviceReadings` (a test rule ordered before the Compose rule) through the test-only seam `DeviceResourcesReader.testSource`. Every device reading in a render is "not observed", so the CPU, disk I/O, storage and since-boot lines no longer carry the build machine's `/proc` counters. Nothing is invented: the screen shows exactly what it shows on a device that reports nothing. `03-storage.png` and `full/03-storage-full.png` were re-recorded for this; the per-shot 0.3% threshold on the tall Storage shot is gone and every shot compares at 0.1%. `storageRenderUsesNoHostReadings` asserts the fixture text is what is rendered. Two consecutive `verifyRoborazziDebug -Pscreens --rerun-tasks` runs passed against the new baselines.

## Re-record log (feature/ux-from-clearance)

The suite now orders an `OnboardingSeen` rule before the Compose rule, so the existing shots still start on the tabs (the first-run introduction is marked as read). Changed and new baselines, all intentional:

| Baseline | Why it changed | Source change |
|---|---|---|
| `01-mine.png`, `full/01-mine-full.png` | New **Now** overview card at the top of Mine (wallet, operations awaiting an outcome, receipt log, vault). | `MineScreen.kt` |
| `06-review.png`, `full/06-review-full.png` | Destination and amount start empty with inline guidance; **Scan QR** / **QR from image** buttons; camera notice; "connect a wallet" hint. | `ReviewScreen.kt`, `ReviewInput.kt`, `UiState.kt` |
| `04-nodes.png`, `full/04-nodes-full.png` | The optional export-scope checkbox row is now one full-width ≥ 48dp toggle (label read with the state). | `NodesScreen.kt` |
| new `07-onboarding.png`, `full/07-onboarding-full.png` | First-run introduction (fresh install, `OnboardingSeen(false)`). | `OnboardingScreen.kt` |
| new `full/07-onboarding-fontscale-1.5.png` | The same at `fontScale = 1.5`, to check that the text wraps rather than clipping. | `OnboardingScreen.kt` |
| new `full/08-review-filled-full.png` | Review form after typing a valid address and `0.01` (no wallet, so Prepare stays off). | `ReviewScreen.kt` |
| new `09-plain-language.png` | `PlainLanguageCard` over a real `TransferReview.prepare` draft built from a fixed test key, destination and blockhash (no wallet, no RPC, so the fee reads *unknown*), and over an unsupported draft. | `PlainLanguage.kt` |

`02-ai`, `03-storage` and `05-receipts` (and their `full/` versions) are unchanged. These are JVM renders, not device screenshots.

### Re-record: agentic redesign (feature/agentic-redesign)

Intentional re-record of every app-shell baseline after the redesign that adapts patterns from OptimAI Agentic for Android
(see `docs/analysis/agentic-android-review.md`). Fresh-install state is unchanged: no wallet, node, host, model or receipts.

| Baseline | Why it changed | Source change |
|---|---|---|
| all `0[1-6]-*.png`, all `full/0[1-6]-*-full.png`, `full/08-review-filled-full.png` | The shell now shows the "Devnet · Review candidate" / "Test SOL only" strip under the header (design.md §3), which moves every screen down. | `ui/EdgeOreApp.kt`, `ui/components/DesignKit.kt` |
| `01-mine*.png` | Heading "Your edge, under your control"; Now rows use icon tiles; the wallet card is a fact card with a "Not connected" pill. | `ui/screens/MineScreen.kt` |
| `02-ai*.png` | Execution and on-device cards use the fact-card layout and status pills; empty conversation text instead of a blank area. | `ui/screens/AiScreen.kt`, `ui/components/Chat.kt` |
| `04-nodes*.png` | Host card gains an icon tile. | `ui/screens/NodesScreen.kt` |
| `05-receipts*.png` | New "Receipt activity" card; with no receipts it shows "Nothing to plot yet" and draws no chart. | `ui/screens/ReceiptsScreen.kt`, `ui/components/Charts.kt` |

`07-onboarding*.png` and `09-plain-language.png` did not change (onboarding has no shell header; the plain-language card is rendered alone).
The new chart, chat bubbles and badges only appear with real local data, so no baseline shows them with values; their logic is covered by
`ChartsAndActivityTest`, `MarkdownTest`, `PlainErrorTest` and `NavBadgesTest`.
