# EdgeORE — production interface specification

Version 3 · mint holographic interface · implementation target, not a qualification certificate · 12 October 2026

## 1. Product and evidence contract

EdgeORE is a native Kotlin/Jetpack Compose workspace for private AI, device-bound storage, owned-host operations, exact-message Solana review, and externally verifiable signed receipt contents. Its settlement program is an undeployed devnet prototype. It does not mine SOL, issue a token, or promise income. Existing source is retained; this document defines the next interface and acceptance criteria. A design preview does not establish runtime behavior.

Baseline: source snapshot is recorded in `SOURCE-PROVENANCE.json`. Existing gate evidence applies to tested commit `250be029b35c`, not automatically to this kit or future edits. Five destinations stay: Mine, AI, Storage, Nodes, Receipts. A dedicated Review route remains the only signature path. A future Mine→Overview rename is a separate migration decision, not applied by this kit.

Every capability has two independent fields: implementation status and observation status. “Implemented in source” never becomes “Observed on device” merely because a unit test passes. Observation states are NOT_RUN, ATTEMPTED, PASS, FAIL and BLOCKED. Every PASS opens its evidence record: environment, UTC time, tested commit, artifact digest, run log and any screenshot. Empty balance means “Balance unavailable”; empty payment means “Not observed.” Never replace unknown with zero.

## 2. Exact design tokens

| Token | Value | Usage |
|---|---|---|
| Background | #060D0C | Entire application canvas |
| Surface | #0D1917 | Cards and sheets |
| Inset | #142421 | Technical details and navigation |
| Border | #2C4740 | 1 dp outlines and separators |
| Primary text | #F2F7F5 | Headings and content |
| Muted text | #ABBDB7 | Sources, timestamps, supporting copy |
| Mint | #19E6BC | Selection and verified-integrity accents |
| Copper | #FF963F | ORE wordmark, Mine selection and attention |
| Copper deep | #E86D35 | Optional action gradient endpoint |
| Danger | #FF6B79 | Failure and destructive action |
| On action | #111514 | Mint button text |

Retain the native three-bar E mark in `ui/components/Components.kt`; do not replace it with a raster logo. Use system sans-serif for normal text and monospace only for hashes, addresses, byte counts and codes. Typography: screen heading 28/32 sp bold; card title 18/24 sp semibold; body 16/24 sp; supporting body 14/20 sp; metadata 12/16 sp medium. Do not shrink metadata below 12 sp. Text scales with Android font settings.

Spacing uses 4 dp steps: 4, 8, 12, 16, 24, 32. Outer phone padding 16 dp; card padding 16 dp; card gap 16 dp; section gap 24 dp. Card radius 20 dp; sheet top radius 24 dp; badge radius 50 dp. Border 1 dp. Primary action minimum 56 dp high; secondary 48 dp; all interactive targets at least 48×48 dp. Never give a small inline icon a smaller hit area.

## 3. Shell and responsive layout

Respect system bar, display cutout and keyboard insets. The top app row contains 30 dp E mark, 10 dp gap, wordmark, and 48 dp Review and Settings actions. Below it, show a compact environment strip: “Devnet · Review candidate”, with build stamp available in About. Do not show a global green “secure” seal.

Below 600 dp use one scrolling content column and a five-item bottom navigation bar. Content width is at most 640 dp. At 600 dp and above, use a navigation rail and a centered content region; at 840 dp, two columns may separate a master list and details. Financial review remains a readable single column, at most 640 dp. Landscape and 200% font size must remain scrollable with actions reachable. Do not put two independent vertical scroll containers inside a single phone page.

Navigation labels remain visible, not icon-only. Selected Mine uses orange icon/text; other destinations use mint. Selection has a visible indicator and label. Storage remains a first-class destination; it has not been replaced with a decorative central action. Back from Review returns to the previous destination without changing the operation state. Back from a wallet app is not authorization success. Browser routes and external links must show destination/origin before leaving.

## 4. Shared component contracts

**Evidence badge:** icon + plain-language text + environment. Mint means a specifically observed property, not universal safety. Copper means pending/attention; danger means failed; muted means missing. A screen reader announces the full property and status.

**Observation tile:** title, value or explicit unavailable text, source, observation timestamp/freshness. Missing metrics have no chart. Charts require actual samples, a time range, unit and accessible data summary. Never create attractive sample trends on production screens.

**Action card:** title, explanation, effect label (Enforced / Saved only / Unavailable), one main action. Disabled actions explain the prerequisite next to the button. A loading button prevents repeated work; it does not erase last-known state.

**Failure panel:** stable code, failing stage, human-readable reason, UTC timestamp, retry affordance when safe, copy-redacted-diagnostics action. Show failures inline until resolved or dismissed. Do not swallow SDK failures into “Not connected.” Do not display raw credentials or unbounded server messages.

**Consent sheet:** purpose, destination host, data categories, retention, maximum bytes/time/charge, exact scope and expiration. Two actions: Cancel and explicit consent. No preselected contribution, download, analytics or remote upload consent.

**Technical disclosure:** expandable exact bytes/digests, selectable and copyable; visible concise summary first. Long identifiers wrap; copy controls announce completion. Never ellipsize the only view of the reviewed recipient.

## 5. Mine destination

Order: Mine heading; single wallet authorization card; balance-observation network selector; SOL/ORE/SKR cards; Edge Node session and contained mint ring; CPU/memory/storage/network cards; additional-worker readiness; daily campaign readiness; live read-only ORE board; review and contribution controls. ORE Bot and notification preferences are shared across destinations.

Status card copy is “No qualified workload. Nothing ran.” unless an implemented, approved workload actually runs. Pause/resume represents policy scheduling, not mining. Do not animate a running ring without measured activity. The Mine art (rock and pickaxe, docs/design/mine-animation.md) swings only while a user-started contribution session is ACTIVE and at least one worker round has returned a result (docs/contribution/session.md): real, measured work on the owner's paired node and storage gateway. It rests otherwise and holds still under reduced motion. The button text "Start Mining" is the owner's choice; the copy beside it says what runs and that nothing is earned.

Wallet card states: disconnected, opening wallet, authorization rejected, connected with balance pending, connected with balance unavailable, connected with observed balance, disconnecting. Show address, Devnet, wallet identity and balance observation time. Authorization failure shows code/stage/time. RPC failure never drops an authorized connection. Disconnect revokes authorization and confirms the result; if wallet revocation cannot be observed, say so.

Supported action card opens Review. Do not make the user's supplied address an automatic recipient. Recipient entry is explicit and validated; amount uses exact decimal-to-lamport arithmetic. Display maximum daily budget and reservation separately from balance.

Device readings show battery, charging, thermal and CPU capability with sources. Sensor absence disables gates requiring that reading. Contribution is opt-in, charge-only by default, with visible battery/thermal/capacity constraints and a stop control. Saved settings must not be labelled enforced unless the scheduler uses them.

## 6. Private AI destination

Two execution choices remain visibly distinct: “Owned host” and “On device.” Changing mode updates the data-flow explanation before sending. Owned-host copy: “Your prompt and selected document are sent to this host.” On-device copy is used only when an actual local engine is selected and ready.

Owned-host sequence: endpoint input → policy validation → list models → choose explicit model → attach bounded UTF-8 document → consent → send → streamed answer → cancel/clear. Loopback HTTP is the special local exception; LAN requires HTTPS; public endpoints are refused under the owned-host policy. Never silently route to a cloud model.

On-device card shows model name/version, source, license, exact size, digest, ABI/runtime compatibility, free-space requirement and execution target. Download states: absent, consent pending, downloading with actual bytes, verifying, ready, loading, generating, cancelling, failed. Completed download requires exact size and SHA-256. A progress bar never substitutes for verification. An interrupted download cannot become Ready. Gated models show “Sign-in required — unavailable” until an approved authenticated path exists.

Chat content remains session-only by default. Document removal explains whether content was already sent to the host. Cancellation records a cancelled state without inventing a final answer. Offline-generation evidence requires a loaded local model and a real prompt with networking disabled; preview text is not evidence.

## 7. Storage destination

Sections: local vault, files, backup status, owned storage endpoint. Local vault copy: “Encrypted on this device with an Android Keystore key.” Display actual device space and enforced file-size limits. SAF file picker controls file import/export. No broad filesystem permissions.

File row: user filename, byte size, created time, encryption state; actions preview/export/delete where implemented. Delete confirmation names the item; interrupted writes clean their temp files; damaged store fails closed and provides exportable diagnostics.

Backup card currently says “Portable backup not built.” A future implementation must keep vault encryption and portable-envelope encryption distinct. Proposed portable backup flow: choose files → passphrase consent → show destination and retention → encrypt authenticated versioned manifest → upload to explicitly configured owned backend → verify retrieval → record backup receipt. No upload happens when toggling a preference.

Restore must work on a clean second device with only the backup and passphrase, without the original Keystore key. Wrong passphrase, wrong object, truncated data, unsupported version and tampered manifest fail before publication. Show “Restore tested” only with a saved second-device run. Do not implement convergent encryption as a privacy shortcut: content equality leakage requires separate review.

## 8. Nodes destination

Top card displays paired host identity, exact endpoint, certificate SHA-256, scopes, last observation and status. Pair wizard: enter endpoint → retrieve/enter expiring challenge → inspect fingerprint through an independent channel → confirm scope → authorize → persist paired identity. Do not auto-accept a newly presented pin.

Health/log actions are bounded scoped reads, not arbitrary remote shell. Stale data stays visible with age and a stale label. Revoke needs signed node acknowledgement; an offline node yields “Revocation pending.” “Forget locally” explains that remote access revocation is unobserved. A hostname and a ping are not proof of capacity or completed work.

Future job subpage remains hidden or marked Planned until settlement deployment and integration gates close. Show Key protection, Execution verification and Settlement state as separate labels. A hardware-bound key signature does not prove the workload executed inside a TEE; StrongBox is key protection, not universal proof-of-compute.

## 9. Receipts destination

Header: “Receipts whose signed contents verify.” Filters: All, Reviews, Node; add job-specific filters only when real receipt types exist. Empty state has no example transactions. Receipt row shows type, UTC time, operation ID, cluster and narrowly scoped integrity result.

Detail panels: exact signed body, body digest, chain position/checkpoint, signing key epoch/trust, wallet signature where present, observation source, settlement status. The original body bytes must be displayed without reserialization. Unsigned descriptive fields are clearly identified. Payment is separate from integrity and from provider acknowledgement.

Export preview lists included/excluded fields and completeness. Standalone verification displays results for integrity, trusted key, completeness and wallet signature independently. The required-envelope policy must be cryptographically bound in a new format version before broad export-integrity claims. Older exports use their original limited coverage; do not retroactively claim newly protected fields.

Tamper demonstration keeps the real original export unchanged and modifies `lamports` within a signed `body` in a copy. PASS original / FAIL copy establishes signed-content integrity only. Synthetic demos are explicitly marked Synthetic and never substitute for a transfer receipt.

## 10. Review route: signature and submission separation

Use one page with six blocks: environment, sender/recipient, exact SOL + lamports, instruction/program/accounts, fee/budget/blockhash validity, message SHA-256 and expandable bytes. Show the cluster at both top and action area. Unknown instruction, stale blockhash, unavailable fee or failed budget validation disables signing and explains why.

Required order: review exact message → persist reviewed operation → recheck block height with the existing 20-block safety window → request wallet signature → verify exact returned message and Ed25519 signature → persist signed bytes → explicit separate Submit action. No `signAndSendTransactions` shortcut may bypass durable reviewed-byte checks.

After submission attempt, show operation ID, signature when known, last observation, reservation and “Observe status.” Timeout, cancellation or ambiguous RPC result becomes outcome unknown. Unknown has no Retry send button. Expiry after attempted submission keeps the reservation until policy has conclusive safe resolution. An unsent expired operation needs fresh review. Discard unsent bytes is available only for bytes proved never submitted.

Recovery display must reflect saved disk state. A state write failure cannot display success. Observation checks current block height/status read-only and never resends. A crash/restart recording must show exactly one submission attempt, not just a final confirmed screen.

## 11. Settlement experience (planned integration)

No earnings dashboard until a funded deployed job and real payout are independently observed. Job review states customer, worker, verifier, program ID, genesis/cluster, escrow lamports, deadline, canonical consent hash and refunds. Terms explain verifier trust and that a hash commits to consent text but does not enforce the text's semantics.

Settlement actions need specific approval separate from this design document. State labels distinguish submitted, observed success, failed and outcome unknown. Explorer links and RPC results accompany observed payouts. Offline receipt checking cannot establish current on-chain payment status. Never stake/slash users based on unreviewed verifier discretion; that is outside this release plan.

## 12. Accessibility and behavior

TalkBack order follows visual reading order; status changes announce once without continuous progress chatter. Use semantic role/stateDescription on switches and buttons, and field errors tied to input. Contrast must be measured, including disabled text and copper backgrounds. At 200% font scale, key content cannot overlap navigation. Support Android keyboard navigation, switch access and reduced-motion settings. No essential action relies on color, swipe, hover, haptics or animation alone.

Motion: 120–180 ms selection transitions; at most 220 ms sheet transitions; no looping ornament. Respect system animation scale. Loading, empty, error, offline, blocked and stale states are designed for every asynchronous card. Preserve entered nonsecret data across rotation. Do not preserve secrets in saved state.

## 13. Implementation map and verification

Existing theme: `app/src/main/java/com/edgeore/app/ui/theme/Theme.kt`. Shared components: `ui/components/Components.kt`. Shell: `ui/EdgeOreApp.kt`. Each screen remains under `ui/screens/`. UI calls existing coordinators; it does not build or broadcast transactions itself. Extract feature state from the large view model in behavior-preserving steps only after runtime triage.

Required visual matrix: 360×800, 412×915, 600×960 dp; 100% and 200% fonts; disconnected/connected/error/unknown; dark mode; screen reader. Retain existing 20 screenshot checks until deliberately re-recorded after reviewing differences. New golden images are not runtime screenshots.

Acceptance: all five destinations and Review are navigable; no clipping; sources and missing states visible; submission disabled for invalid review; unknown outcome offers observation only; a corrupted store disables transfers; errors remain visible; no unsigned metadata represented as signed; no synthetic readings on production screens. Capture screenshots from the exact qualified APK, with device identity and build stamp. `docs/production/ui-preview.html` is a static design reference with illustrative UI, not an Android runtime capture.

## 14. Release boundary

A production release needs qualified device runs, owned-host tests, approved portable-backup implementation, deployed/tested settlement integration if advertised, owner release signing, license decision, privacy/retention policy, final dependency/license review, clean-clone build and published artifact identity. Feature scope can be reduced instead: hide unsupported production features and state the narrower supported contract. No prompt, ZIP, screenshot score or test count independently grants production readiness.


## 15. Approved mint holographic implementation, 12 October 2026

The supplied `DESIGN (2).md` is retained at [docs/design/mint-holographic-v2.md](docs/design/mint-holographic-v2.md) as the requested integration target. It is a specification, not evidence that integrations are running. This section records the actual implementation boundary; remaining requirements in the supplied specification remain acceptance work.

Shared source: `ui/theme/Theme.kt` and `Tokens.kt`; rounded outline registry `ui/components/Icons.kt`; `EdgeCard`, `PrimaryAction`, `SecondaryAction`, `ResourceControl`, `StatusPill`, `TokenBalanceCard`, `WorkerStatusRow`, `WalletConnectSheet`, `LoadingState`, `EmptyState`, and `IconAction`. Cards use a restrained graphite gradient and a mint decorative border. Ordinary glyphs are local outline vectors. Neutral token icons are explicitly not official brand assets.

Balance network is an **observation** selector. Devnet, Testnet and Mainnet use separate public HTTPS RPC endpoints and checked genesis hashes. Wallet authorization, exact-message review, signing, submission and restart recovery remain devnet-only. Selecting an observation network does not change a financial operation's cluster or invalidate its recovery ledger. The UI explains this boundary before any action. Full testnet/mainnet transaction support is not implemented.

SOL is read via `getBalance`, with integer decimal formatting. Mainnet ORE's mint is pinned to `api/src/consts.rs` at upstream commit `48c203bd75db3cc45105ec29d8f8db719e5a2263`; mint owner and decimals are checked, then every matching owner token account is summed with arbitrary-precision integers. SKR has no verified registry entry yet and is unavailable. No ORE/SKR deployment is asserted on development networks. Reads refresh on connection, network observation change, activity resume and while Mine is visible. Generation/account/network guards discard superseded responses. Failed refreshes preserve previous observed amounts with a Stale label and timestamp. WebSocket subscriptions remain follow-up work.

Resource cards sample Android logical CPU count, ActivityManager memory, StatFs accessible filesystem and ConnectivityManager connection type. UID TrafficStats measures only this app's aggregate bytes since boot. These readings are not worker throughput or whole-device utilisation. No fabricated progress bars. The screenshot suite uses an explicit unavailable-readings test seam; production uses native sources.

The existing node/gateway session remains the only Mine execution path. The ring waits for a first worker result before moving, stops outside ACTIVE, when hidden or in the background, and respects reduced motion/power saving. Impact particles are decoration and never change balances. Bandwidth/cloud workers show Setup required. There is no server eligibility service or funded daily campaign: no local 0/5 counter, check-in payout or one-hour entitlement is invented.

ORE's 25-cell mainnet board remains a validated read-only observer. Unobserved cells show dashes, not zero bids. Deploy is disabled regardless of wallet connection because an ORE transaction adapter has not been implemented. Detailed source/RPC/advisor controls remain available on the board route.

ORE Bot offers deterministic, timestamped help grounded in current app state. It cannot sign, submit, alter limits or start work. Optional local Android reminders require an explicit toggle and Android notification permission. WorkManager handles background delivery around 10:00 local time, at most once per local day and outside 21:00–09:00 quiet hours. Android can delay delivery. The inbox states that no server inbox exists. Campaign eligibility notifications, configurable categories/hours, enhanced bot conversation and push delivery remain unconfigured.

Device wallet, actual balance matching, notification delivery, TalkBack and live provider gates require saved runtime evidence. Compile/JVM/render results are not device qualification. See [implementation report](docs/design/mint-implementation-report.md).
