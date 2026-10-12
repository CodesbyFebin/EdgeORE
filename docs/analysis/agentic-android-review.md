# Agentic-android review and what EdgeORE took from it

Source reviewed: https://github.com/CodesbyFebin/Agentic-android, commit `94b6db3` (2 commits), "OptimAI Agentic for Android".
Kotlin/Compose, package `com.test.agenttrade`, MIT License, Copyright (c) 2026 OptimAI Agentic contributors
(upstream OptimaiNetwork/optimai-agentic-android). 42 Kotlin source files, about 10,800 lines, one unit-test class.

Decision: EdgeORE stays the product (Solana devnet, Mobile Wallet Adapter, `com.edgeore.app`). This app was not rebranded.
Only general Android UI patterns were adapted, rewritten against EdgeORE's colours, `design.md` rules and existing
coordinators. Nothing about trading, BNB Chain, PancakeSwap, Reown or third-party logos came across.

## 1. What the app is

A tokenized-stock ("bStocks") trading client for BNB Smart Chain. It browses a stock catalog with charts, quotes and executes
swaps through PancakeSwap routers, connects MetaMask or Trust Wallet through Reown (WalletConnect) and shows a portfolio and
activity log. An "agent" tab sends questions to the OptimAI server and renders answers, technical-indicator cards and order
previews. It also ships an input-method keyboard (ticker toolbar, trade cards) and a share/process-text target ("Trade with OptimAI").

## 2. Real versus mocked

| Area | Finding |
|---|---|
| Market data, quotes, candles, portfolio, transactions | Real HTTP calls (`data/StockApiClient.kt`, OkHttp) to the hosted server `https://agentic-api.optimai.network`. No hard-coded prices or sample series were found. |
| Agent chat | Real, but **server-side**: `/agent/ask-optimai` and `/agent/ask-optimai-pro` receive the user's text, recent history and the wallet address. Not on-device. The first chat bubble is a fixed greeting and the suggestion grid is canned prompts. |
| Wallet | Real Reown Sign client (`wallet/WalletConnectManager.kt`): pairing, session, `eth_sendTransaction`, chain switch to BSC. |
| **Simulated wallet (mock)** | Debug builds only (`BuildConfig.DEBUG`) and only after a Settings switch labelled "Simulated wallet · signs nothing, records nothing". It invents an address from a UUID, returns `0xSIMULATEDTX…` as a transaction hash, and `waitForReceipt` returns `SUCCESS` for it, so a debug build can show a completed order that never happened. Labelled and off by default, but it is a fabricated success path. |
| TradeGuard | Real and tested: refuses swap targets and approval spenders that are not known PancakeSwap routers, checks the `approve` calldata encodes the same spender, and refuses other chains. `txHash` requires 32 bytes (but the simulated path bypasses it). |
| UserFacingError | Real mapping of exceptions to short sentences; drops any message that looks technical. |
| Keyboard IME, share target | Real `InputMethodService` and `ACTION_SEND` / `ACTION_PROCESS_TEXT` activity; both call the same server. Settings has a "Share a Sample X Post" button that feeds a fixed sample text to the share flow (labelled as a sample). |
| Splash | Pure Compose animation of the OptimAI mark (about 2 s, light sweep, pulsing glow). |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `RECORD_AUDIO` (dictation and keyboard mic). Release network config is HTTPS-only; debug allows a local cleartext host. |
| Bundled marks | `wallet_metamask.png`, `wallet_trust.png`, `chain_bnb.png`, `tab_logo_bstocks.png` are third-party trademarks (its own `THIRD_PARTY_NOTICES.md` says so). |

## 3. Build attempt (honest result)

Copy at `/workspace/agentic-build`, JDK 17, Gradle 8.13 wrapper, AGP 8.13.1, Kotlin 2.2.20, compileSdk 36, **no `reown.projectId`**
set (no `local.properties` value, no `REOWN_PROJECT_ID` environment variable):

```
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug   ->  BUILD SUCCESSFUL in 2m 28s, exit 0
Unit tests: com.test.agenttrade.data.TradeGuardTest  9 tests, 0 failures, 0 errors
APK: app/build/outputs/apk/debug/app-debug.apk  54,901,372 bytes
     sha256 a789735f50e4386b324298aa50cb30237edef39736a7293f31c3b460511ebe05
```

The missing project id does **not** break the build: `BuildConfig.REOWN_PROJECT_ID` is compiled as `""`. It would break wallet
connection at runtime (Reown initialisation with an empty id; the app only logs "Reown setup failed"). The APK was not installed or
run on a device or emulator, so no runtime behaviour is claimed. Compiler warnings: deprecated icons and a deprecated Reown `connect`.

## 4. Reuse verdicts for EdgeORE

| Part | Verdict | Why |
|---|---|---|
| Design-system structure (`ui/theme/DS.kt`) | **Adapted** → `ui/theme/Tokens.kt` | One object per scale and "screens use tokens" is good structure. Values are EdgeORE's (`design.md` §2: 4 dp steps, 16 dp cards, 50 dp badges, ≥12 sp metadata). Its zinc/green palette and light theme were not taken; EdgeORE colours stay in `EdgeColors`. |
| Components (`ui/components/Components.kt`) | **Adapted** → `ui/components/DesignKit.kt` | `DSBadge` → `StatusPill` (always text, optional icon), `CountBadge`, `DSIconTile` → `IconTile`, `DSDivider` → `EdgeDivider`. Its 11 sp badge text was raised to 12 sp. `DSButton` not taken: EdgeORE's `PrimaryAction`/`SecondaryAction` already meet the 56/48 dp targets. `plainClickable` (no ripple, no role) not taken. |
| Chart rendering (`ui/components/Charts.kt`) | **Adapted** → `ui/components/Charts.kt` | `Domain`, `plotPoints`, Fritsch–Carlson `monotonePath`, `areaPath`. Added a count domain from zero and a required spoken summary; dropped the 800 ms `DrawIn` reveal (`design.md` §12 motion limits). Used only for receipts per day from the local receipt log, with an empty state. |
| Markdown (`ui/components/Markdown.kt`) | **Adapted** → `ui/components/Markdown.kt` | Inline bold/italic/code for model output. Links are deliberately **not clickable**: model output is untrusted, so `[label](url)` renders as `label (url)` with the destination visible (`design.md` §3). |
| Agent-chat UI (`ui/agent/AgentChatScreen.kt`) | **Adapted (layout only)** → `ui/components/Chat.kt`, `ui/screens/AiScreen.kt` | Avatar + model bubble left, high-contrast user bubble right. Each bubble names its source in text. The animated "Thinking" dots became a static working row shown only while a real request runs. Not taken: greeting bubble, canned suggestion grid, dictation, server calls, order-preview cards. |
| Technical-card layout (`ui/agent/TechnicalCards.kt`) | **Adapted (layout only)** → `FactCard` + `StatCell` in `DesignKit.kt` | Header (tile, title, subtitle, pill) / content / source footer. Used for the on-device model, AI execution and Mine wallet cards, filled only from existing state. Indicator charts (RSI, MACD, Bollinger), signal gauge and "Trade" button are trading content and were not taken. |
| Error mapping (`data/UserFacingError.kt`) | **Adapted** → `ui/components/PlainError.kt` | The plain-language test was kept, but EdgeORE does not throw the technical text away: self-hosters need messages such as a TLS pin mismatch, so the original (capped at 300 chars) sits behind "Show details". Display-only; controller error values and receipts are unchanged. |
| Guard pattern and tests (`TradeGuard`) | **Not ported (pattern already present)** | EdgeORE already refuses before any wallet prompt, more strictly: exact-message review, wallet-return verification against the reviewed message, durable reservation before network side effects. TradeGuard's checks are EVM/PancakeSwap specific. Its test style (one small guard, many refusal cases) matches EdgeORE's existing `WalletAuthorizationTest`/`StoreFailureAndSigningGateTest`. |
| Splash (`ui/Splash.kt`) | **Rejected** | A 2 s branded animation with a light sweep and pulsing glow conflicts with `design.md` §12 (no looping ornament, short transitions) and delays first content. Not needed; EdgeORE starts on content. |
| App router (`ui/AppRouter.kt`) | **Rejected** | A process-wide singleton for deep-link/keyboard/share hand-offs into a quote sheet. EdgeORE has no deep links and keeps routes in `rememberSaveable`; a global router would add state outside the Activity for no current caller. Only the tab-badge idea from `AppRoot.kt` was used (`ui/NavBadges.kt`). |
| Share target (`share/ShareActivity.kt`) | **Backlog, not added** | See §6. |
| Keyboard IME (`keyboard/*`) | **Backlog, not added** | See §6. |
| `RemoteIcon`, `Logo` | **Rejected** | Remote logo loading needs Coil + network for icons EdgeORE does not show; `Logo` draws the OptimAI mark. EdgeORE keeps its native E mark (`design.md` §2). |
| Trading, BNB Chain, PancakeSwap, Reown, stock catalog, portfolio, quote screen | **Out of scope** | Not EdgeORE's product; would also break the "no trading" rule. |
| MetaMask / Trust Wallet / BNB / bStocks / OptimAI images | **Out of scope** | Third-party trademarks; none were copied. |

## 5. What changed in EdgeORE (branch `feature/agentic-redesign` from `main` `268fd74`)

| Commit | Files | Connected to |
|---|---|---|
| `ui: design tokens and shared kit…` | `ui/theme/Tokens.kt`, `ui/components/DesignKit.kt`, `NOTICE` | — |
| `ui: plain-language error headline…` | `ui/components/PlainError.kt`, `ui/screens/NodesScreen.kt`, `PlainErrorTest` | `NodeState.error` |
| `receipts: activity chart…` | `ui/components/Charts.kt`, `ui/ReceiptActivity.kt`, `ui/screens/ReceiptsScreen.kt`, `ChartsAndActivityTest` | `vm.receipts` (the local signed receipt log) |
| `ai: chat layout and model fact card…` | `ui/components/Markdown.kt`, `ui/components/Chat.kt`, `ui/screens/AiScreen.kt`, `MarkdownTest`, `docs/on-device-ai.md` | `OnDeviceAiController` state and messages; owned-host `AiState` |
| `mine: heading, environment strip…` | `ui/NavBadges.kt`, `ui/EdgeOreApp.kt`, `ui/screens/MineScreen.kt`, `NavBadgesTest` | `vm.operations` (durable operations), `vm.receiptDamage`, `vm.walletState`, `vm.balance` |
| `screens: re-record baselines…` | `docs/screenshots/**` | intentional re-record, reasons in `docs/screenshots/README.md` |

Not touched: `wallet/`, `solana/` (review, operations, RPC, signing), `receipts/` (format, signing, verification), `storage/`,
`contribution/`, `node/` protocol code, `ReviewScreen.kt`, `EdgeOreViewModel.kt`, the Storage screen and the receipt checker.
Every existing gate (signing gate, unknown-outcome observation-only, store-failure disable, consent dialogs) is unchanged.
No dependency was added or changed. No secrets or keystores were committed.

Honesty rules kept in the new UI:
- The chart plots only stored receipts per local day; with none it says "Nothing to plot yet" and draws nothing. Unparsable times are counted, not guessed.
- Chat shows only messages the real controllers produced. No greeting, no canned answer, no cloud key, no cloud route.
- Fact cards show missing values as "Not observed" (copper), never zero.
- Badges count only local state and disappear at zero.
- The environment strip says "Devnet · Review candidate" and "Test SOL only" (`design.md` §3); no "secure" seal, no production claim.

## 6. Backlog ideas (not built)

- **Share target for addresses.** An `ACTION_SEND` text/plain activity could pass a shared Solana address into Review's destination
  field (prefill only, through the existing `AddressQr`-style parser, never auto-submitting). Not added now: it is a new exported
  entry point, the QR scanner and paste already cover the need, and it needs its own threat review (spoofed text, confusable
  addresses) and tests before it is "clearly useful and safe".
- **Keyboard (IME).** Rejected for now. A keyboard sees everything the user types in every app; EdgeORE has no use that justifies
  `BIND_INPUT_METHOD` and the privacy review it would need.
- Tab transitions (`AnimatedContent` push) within the 120–180 ms limit, respecting the system animation scale.
- Navigation rail at ≥600 dp (`design.md` §3), which neither app has yet.
- A Compose render test with receipts present, to cover the chart drawing path in addition to its unit-tested geometry.

## 7. Gates (tested commit `421508a`)

JVM/build results only. No phone, wallet, owned AI host or node session is part of them. Evidence: `evidence/build-421508a8724d/`.

| Gate | Result |
|---|---|
| `SCREENS=1 scripts/qualify.sh` (clean, unit tests, lint, debug APK, androidTest APK, release manifest, screenshots) | exit 0 |
| Android unit tests (`:app:testDebugUnitTest`) | 29 suites, 265 tests: 264 passed, 0 failures, 0 errors, 1 skipped (`NodeAgentIntegrationTest`, needs a live node agent). This branch adds 4 suites / 11 tests: `ChartsAndActivityTest` 5, `PlainErrorTest` 3, `MarkdownTest` 2, `NavBadgesTest` 1. |
| Receipt checker (`bash gradlew -p tools/receipt-checker test installDist`) | exit 0; 1 suite, 14 tests, 14 passed (counted separately from the Android tests) |
| Lint (debug) | No issues found |
| Release manifest (`scripts/check-manifest.py`) | MANIFEST CHECK: PASS |
| Screenshots (`verifyRoborazziDebug -Pscreens`) | exit 0 against baselines re-recorded intentionally in `00e6cf4` (reasons in `docs/screenshots/README.md`) |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 57,681,080 bytes, sha256 `f49dcca23140b63f26791bb6347f31d53f2769d468f2b9e5e5776d222fa30c1b`, debug-signed, `BuildConfig.GIT_COMMIT = 421508a8724d` |

The receipt checker still only *verifies the integrity of signed receipt contents*; nothing in this redesign changes what it checks.
Before/after renders and a contact sheet were produced outside the repository from the committed baselines (before = `main` `268fd74`, after = this branch); they are JVM renders, not device screenshots.
