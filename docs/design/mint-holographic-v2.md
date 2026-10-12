# EdgeORE — Web3 Design and Full Integration Specification v2

## Copy-ready instruction

Refine the existing EdgeORE app to match the latest approved mint holographic Web3 design. Apply this specification across Mine, AI, Nodes, Receipts, wallet connection, transaction review, settings, dialogs, loading states and empty states. Preserve existing functionality and truthful status values. Deliver a coherent product interface with reusable theme tokens, components and a complete icon mapping. This is a design and implementation prompt; it does not claim that the app has already been modified.

## Visual direction

Use a deep obsidian canvas, restrained emerald ambient light, graphite glass surfaces, mint outlines and warm orange accents. Keep the EdgeORE wordmark: Edge in off-white and ORE in orange. Retain the three horizontal mint-to-orange brand strokes. Mining visuals are compact stylized graphite rocks with luminous mint crystalline seams, contained within circular holographic frames. Keep the UI readable and calm; glow belongs primarily to mining illustrations and selected controls.

These hex values are proposed implementation tokens matching the visual reference, not pixel-sampled measurements.

## Colour tokens

| Token | Value | Usage |
|---|---|---|
| background | #060D0C | Main canvas |
| surface | #0D1917 | Cards and navigation |
| surface-raised | #142421 | Dialogs and inputs |
| surface-pressed | #1C3530 | Pressed controls |
| mint | #19E6BC | Active controls, focus, mining rings |
| mint-soft | #8BF4DF | Subtle highlights |
| orange | #FF963F | ORE wordmark, paused badge, selected Mine |
| text-primary | #F2F7F5 | Headings and important values |
| text-secondary | #ABBDB7 | Supporting text |
| text-muted | #81968F | Tertiary labels |
| border | #2C4740 | Standard outlines |
| border-active | #19E6BC | Focused or selected outline |
| success | #43D99B | Verified success only |
| warning | #FFB65C | Warnings and paused states |
| error | #FF6B79 | Failed operations and changed-message warnings |
| disabled-surface | #182421 | Disabled controls |
| disabled-text | #89968F | Disabled labels |

Use mint at approximately 25–40% opacity for decorative borders. Use a solid mint focus outline. Decorative glow: mint at 12–18% opacity, soft radius 12–24px. Do not glow body text. Keep official Solana and ORE marks recognisable in their official colours rather than recolouring brand assets arbitrarily.

## Layout and typography

- Phone-first layout; use 16dp horizontal padding, 16dp card gaps and an 8dp spacing grid. On web, use equivalent CSS pixels and a centred 440px maximum content width.
- Main headings: 28–32sp, semibold. Section titles: 20–22sp, semibold. Body: 14–16sp. Supporting text: 12–14sp. Use tabular numbers for amounts and metrics.
- Use the platform sans-serif consistently. On web, use Inter with a system fallback; use monospace only for hashes, addresses and compact technical labels.
- Cards: 20dp radius, 1dp border. Inputs and buttons: 14dp radius. Pills: fully rounded. Minimum touch target: 48dp.
- Align all section headings, cards and controls to the same content edges. Keep content clear of the bottom navigation and device safe areas.
- Use real scrolling in the app. A full-page showcase image may display the whole scrollable page, but should not force tiny content into a physical phone viewport.

## Complete icon system

Use one rounded outline family throughout. On Android use Material Symbols Rounded in outline style or equivalent local vector assets; on web use equivalent SVGs from the same family. Standard icons: 24dp; small supporting icons: 18–20dp. Keep stroke weight visually consistent at about 1.75–2px on a 24px canvas. No emoji, mixed filled/outline families or raster icons for ordinary controls. The brand marks and mining illustrations are explicit exceptions.

| Location or action | Icon motif | Theme treatment |
|---|---|---|
| Mine navigation | Pickaxe | Orange when selected; secondary text otherwise |
| AI navigation | Rounded spark inside chat outline | Mint when selected |
| Central navigation action | Existing circular EdgeORE mark | Off-white ring; mint focus outline; preserve action |
| Nodes navigation | Three connected rounded squares | Mint when selected |
| Receipts navigation | Document with check | Mint when selected |
| Header review shortcut | Check inside rounded square | Secondary; mint when selected |
| Settings | Rounded gear | Secondary |
| Theme toggle | Sun / crescent moon | Secondary |
| Wallet connection | Wallet outline | Mint accent |
| SOL balance | Official Solana mark | Official asset colours |
| ORE balance | Official ORE mark | Official asset colours |
| Edge Node | Node ring / connected cube | Mint |
| CPU | Microchip | Mint |
| Memory | Memory module | Mint |
| Storage | Database cylinder | Mint |
| Network | Connection / signal outline | Mint |
| Information | Circle with lowercase i | Secondary |
| Charge-only | Plug with lightning bolt | Mint when enabled |
| Thermal guard | Thermometer with shield | Mint when enabled |
| Battery reserve | Battery with shield | Mint when enabled |
| Explore | Compass | Mint when selected |
| Rewards | Gift outline | Mint when selected |
| Social | Two people / chat bubbles | Mint when selected |
| Studio | Rounded sliders | Mint when selected |
| Location / test zone | Map pin with ring | Mint |
| Check in | Pin with check | Mint |
| Start / resume | Play triangle | Mint |
| Pause | Two rounded bars | Orange |
| Stop | Rounded square | Error only for destructive stop |
| Refresh | Circular arrow | Secondary; mint on focus |
| Copy address / hash | Overlapping rectangles | Secondary |
| Open external link | Arrow pointing out of square | Secondary |
| Back / next | Rounded chevrons | Primary |
| Close | Rounded X | Secondary |
| Search | Magnifying glass | Secondary |
| Filter | Rounded sliders | Secondary |
| Export / download | Down arrow into tray | Mint |
| Upload / backup | Up arrow into tray | Mint |
| Verified receipt | Shield with check | Success |
| Pending receipt | Clock | Warning |
| Failure / warning | Triangle with exclamation | Error / warning respectively |
| Approval / security | Lock or shield | Mint; error on MESSAGE_CHANGED |
| Increment / decrement | Plus / minus | Primary |

For any unlisted action choose the nearest meaningful motif within the same family and add it to the shared icon registry. Give every icon button an accessible label. Hide purely decorative icons from accessibility APIs.

## Screen and component rules

### Header and wallet

Keep the EdgeORE logo and compact review/settings shortcuts. Render DEVNET · REVIEW CANDIDATE as an outlined mint pill. Wallet connection sits in a clean row with a 48dp-high Connect button. Balance cards share dimensions and padding. Unavailable balances remain explicit; never replace them with invented amounts.

### Edge Node

Place a compact pickaxe-and-rock illustration inside a circular mint holographic frame, approximately 160–200dp across. Use graphite materials and mint crystal seams with minimal orange sparks. Keep all rocks, particles and shadows inside the section; nothing spills over CPU or memory cards. Put the title and Paused badge on the same row. Resource cards use the same outline icons and typography as all other cards. Capacity and utilisation must be visually distinct: do not show arbitrary progress bars when only CPU core count or memory class is known.

### ORE Mining

Use a smaller matching circular ore emblem in the same graphite/mint visual language. Keep the ORE brand mark identifiable. Lite/Pro is one segmented control. Show SOL amount clearly with tabular numerals. Align +0.01, +0.1, +1 and MAX in four equal-width controls. Align Rounds and Per round labels with their values. Deploy remains disabled while prerequisites are unmet; explain the prerequisite below the button. Do not imply mining, payout or deployment has occurred without confirmed evidence.

### Contribution

Use one consistent settings row component: outline icon, label, supporting status and switch. Enabled switches use mint with a dark track and white thumb; disabled/off switches use a graphite track. Charge-only, Thermal guard and Battery reserve must retain accurate Saved only or enforced state. Do not imply saved preferences are currently enforced.

### Bottom navigation

Use the same icon dimensions, label size and spacing for Mine, AI, Nodes and Receipts. Active Mine uses orange; other active sections use mint. Inactive items use secondary text. Pair colour with a selected indicator and label weight. Preserve the central action and provide a clear accessible label. Navigation must not obscure content.

### Remaining screens

AI chat uses graphite bubbles, restrained mint borders and readable message text. Nodes use the same resource cards and status badges. Receipts use document/check icons, evidence hashes and truthful verification states. Transaction review uses clear instruction summaries and exact-message binding status. MESSAGE_CHANGED uses error colour, explicit text and disabled approval. Settings, dialogs, sheets and empty states reuse these exact tokens and components.

## Motion and accessibility

- Ordinary state transitions: 150–220ms with gentle ease-out. Avoid flashing neon or constantly moving decorative backgrounds.
- If mining is actually running, animate a small pickaxe swing and restrained particles inside its circle. Freeze the animation when paused, stopped, hidden or blocked. A paused state must not show ongoing work animation.
- Respect reduced motion and power-saving preferences. Use a static mining illustration as fallback.
- Target at least 4.5:1 contrast for normal text and 3:1 for large text and essential control boundaries; verify actual composited colours.
- Support text scaling, keyboard focus on web and TalkBack on Android. Do not convey state solely by colour.

## Implementation prompt and acceptance checklist

Inspect existing screens and assets first. Create one shared theme/token source, one icon registry and reusable card, button, input, status badge, settings row and navigation components. Replace inconsistent existing icons across the full app with the mapped outline family. Reuse existing official brand assets. Preserve routes, wallet logic, transaction review checks and data bindings. Implement the functional integrations below alongside the shared visual system. This document specifies desired work; claim completion only after the acceptance gates pass.

Deliver the final design with all of the following checked:

- Every screen uses the shared colour and typography tokens.
- Every ordinary icon comes from the same outline family and uses consistent sizing.
- Edge Node and ORE Mining illustrations share materials, rings and lighting.
- All cards, controls, borders and corner radii follow the same component rules.
- Mining motion reflects real state and stops while Paused.
- No fabricated balances, utilisation, earnings, work or verification status.
- No clipped content, overlapping navigation or low-contrast labels.
- Small phones, increased font size, disabled states, focus states and reduced motion are reviewed.
- Report which screens and assets changed and distinguish visual review from runtime testing.

## Image-generation prompt

Create a polished complete portrait EdgeORE app dashboard matching this specification: obsidian background, graphite glass cards, restrained mint holographic outlines and orange brand accents. Preserve the approved section order and all truthful disconnected/paused/zero-value states. Put a compact graphite pickaxe striking a mint-veined angular rock fully inside the Edge Node circle. Match the ORE Mining circle to that same material and lighting system. Use uniform rounded outline icons for wallet, settings, CPU, memory, contribution toggles and navigation. Keep all text crisp, spacing consistent and controls aligned. Show the complete page without cropping. No phone frame, promotional slogans, fabricated metrics or oversized artwork spilling across cards. The result is a static visual concept unless animation is separately requested.


## v2 product requirements — authoritative implementation contract

The sections below supersede earlier two-token balance layouts and visual-only assumptions. Preserve the approved mint holographic theme and icon rules. Upgrade the existing application rather than replacing its working architecture. Inspect the actual repository, build instructions and current integrations before changing dependencies. Keep native Kotlin/Compose as the native client if that is the repository foundation; use React only for an existing or explicitly scoped web client. Do not claim code implementation from this document alone.

### Home page order

1. EdgeORE header, network selector, notification bell and settings.
2. One Connect Wallet button above all balances; after connection show wallet name, shortened address, network and account actions.
3. Three aligned SOL / ORE / SKR balance cards with official token marks and individual loading/error states.
4. Compact daily session and check-in panel, with ORE Bot help shortcut.
5. Edge Node circular pickaxe-and-rock animation, real runtime status and Mine / Pause / Stop controls.
6. CPU / Memory / Storage / Network telemetry in a consistent 2×2 resource card layout.
7. ORE Mining section with Lite/Pro controls, SOL allocation, rounds, fee summary, transaction review and Deploy.
8. Live 5×5 ORE grid with current round details and connection freshness.
9. Contribution preferences and persistent bottom navigation.

### One wallet connection surface

Provide a modern dark-glass wallet chooser with detected wallets first, supported wallet options, availability labels, installation/deep-link actions where supported, connection progress, cancellation, retry, account switch, disconnect and reconnect. Detect capabilities instead of promising compatibility with every wallet. Never ask for seed phrases or custody user keys on the backend.

RainbowKit is an Ethereum/EVM React connector stack based on wagmi/viem and EIP wallet standards. Do not use it as the Solana transport. Satisfy the requested RainbowKit experience through a similarly polished Solana chooser. Use Solana Wallet Standard discovery and compatible Solana adapters on web; use Mobile Wallet Adapter on native Android/Seeker. A multi-chain wallet still needs its Solana account and signing capability. If RainbowKit is included for separately requested EVM support, keep that connection and balances separate from SOL/ORE/SKR.

Support compatible native wallets through MWA and publish a tested wallet/platform/cluster capability matrix. Surface unsupported capabilities explicitly rather than failing silently. Wallet connection is not backend authentication: request a separate domain-bound, nonce-based sign-in proof where supported, verify it server-side, expire it and prevent replay. Provide an explicit limited read-only mode when signed authentication is unavailable; it cannot claim rewards or control authenticated jobs.

### Devnet / Testnet / Mainnet

Expose all three clusters with a persistent visible badge. Start development on Devnet; remember the user's explicit network selection. Store independent RPC HTTP/WS URLs, expected genesis identity, token mint registry and ORE program deployment registry for each cluster. Validate the endpoint identity before transactions. On network change stop pending workflows, cancel or explicitly resolve active sessions, clear account subscriptions, invalidate balances and unsigned transaction reviews, then reconnect or reauthorise the wallet as required.

Do not assume official ORE/SKR assets or an ORE mining program exist on all clusters. A cluster with no verified deployment shows Not available on this network. Label development mints Test ORE / Test SKR; never present them as official mainnet holdings. Keep rewards, receipts, round state and backend sessions isolated by cluster.

### Real SOL / ORE / SKR balances

Read SOL through getBalance and format lamports with integer-safe arithmetic. Read all owner token accounts for each verified mint and sum raw token amounts with mint-derived decimals; handle the mint's actual token program, including Token-2022 if applicable. Validate mint account existence, owner program and metadata against an authoritative registry. Do not infer identity from ticker symbols, and do not invent ORE/SKR mint addresses.

Query on connect, account change, network change, foreground resume and confirmed transactions. Subscribe to relevant accounts; reconcile periodically with backoff and rate limits. Tag every result with account, cluster, observed slot, commitment and timestamp. Discard responses from a previous account or cluster. Use Loading / Connected / Stale / RPC unavailable / Not supported on this network states. Display 0 only after a successful read proves zero. Preserve stale values with a timestamp and stale label; do not silently reset them to zero.

### Mine button and node orchestration

One Mine click requests a coordinated Edge Node session using the user's previously selected resource limits. On first use, explain and save separate opt-ins for bandwidth, storage and linked cloud hosting, with explicit quotas and cost limits. The Mine button does not automatically sign a SOL deployment transaction or provision billable cloud resources without a reviewed authorisation.

Session state: Idle → Starting → Running → Paused / Stopping → Stopped / Completed; include Blocked and Failed with reasons. Starting becomes Running only after worker acknowledgement. Each bandwidth/storage/cloud worker exposes its own state, provider readiness and measured contribution. Partial failure must not display All running. An unconfigured provider shows Setup required. A connected worker with no assigned work shows Waiting for work; connectivity alone does not prove useful contribution.

Bandwidth work requires a real approved relay/job provider and metered app-owned traffic. Storage work requires a real assigned allocation, integrity checking and receipts. Cloud hosting requires a linked supported host/agent, actual workload allocation and a health check. A phone or browser alone cannot create arbitrary cloud capacity. Preserve host isolation and workload restrictions already in the project.

Server start/stop endpoints use idempotency keys, authenticated ownership, cluster-scoped session records, signed agent registration, leases and heartbeat expiry. Stop cancels leases and jobs and verifies worker acknowledgement. Persist enough state to recover after app restart. Resume reconciles authoritative backend state rather than starting duplicate workers. Enforce battery, thermal, charge-only and metered-network limits in the worker, and show Saved only until enforcement is actually supported.

### Pickaxe GIF and ORE particles

Use the supplied mining GIF or a theme-matched animation derived from it, clipped within the mint ring. Start motion only after Running is acknowledged; show a small Starting indicator before that. Pause/freeze on paused, blocked, failed or completed sessions. For reduced motion use a static frame with a text status.

Generate intermittent decorative ORE particles on pickaxe impacts. These particles indicate animation only and must not increment token balances or earned counters. Actual earned counters change only on verifiable job/reward events; confirmed payouts update the wallet balance from chain reads. Stop motion off-screen and respect power-saving limits. Use lifecycle-aware animation and a worker/service appropriate to the platform; do not rely on a browser tab remaining alive for one hour.

### Real resource telemetry

| Card | Real source and labels | Forbidden fallback |
|---|---|---|
| CPU | Native/runtime logical CPU count; optional separately sampled app/worker usage | Hardcoded 6 cores |
| Memory | Native total/available memory and app usage where permitted; web approximate device memory clearly labelled | Hardcoded 4 GB class |
| Storage | Accessible filesystem total/free and app allocation; web origin quota/usage clearly labelled | Hardcoded 250.00 GB or presenting origin quota as device disk |
| Network | Actual connection type, worker connection and app/session upload/download bytes; rate from byte deltas | Invented throughput or inferring whole-device traffic |

Report source, sample time and unavailable permissions. Distinguish capacity, availability, allocated resources and usage. At idle show Idle with a steady muted dot; if the requested idle blink is retained, make it a slow decorative status pulse labelled Idle, never an activity signal. Pulse activity only on measured transfer events, and disable pulsing for reduced motion. Cloud host telemetry comes from the linked host, clearly separate from phone telemetry.

### Daily sessions, check-in and real rewards

Implement an EdgeORE-managed eligibility policy: maximum five session starts per server-defined day, maximum one hour per session, one active session per eligible account, server-enforced expiry and a visible reset time converted to the user's timezone. Define the policy day as UTC and display it. Count an acknowledged session start once; retries are idempotent. Pausing does not reset or extend the wall-clock expiry. Failure before worker acknowledgement does not consume a session. Server time is authoritative; changing the device clock cannot bypass limits. Freeze eligibility policy version for the session and record it in the receipt.

Show Sessions used 0/5 and session time remaining from server state. Provide a separate Daily check-in action with one server-validated claim per policy day. Define reward amount, eligibility, anti-abuse controls, sponsor treasury, mint, payout fees, caps and failure handling before enabling payouts. Work contribution may generate a receipt without guaranteeing a reward.

Use Free ORE collection only when a funded campaign actually covers the described costs and pays verified ORE. Otherwise display Daily contribution session and Rewards unavailable / Awaiting funding. Five one-hour turns are this app's policy, not an asserted ORE protocol rule. Local timer completion cannot mint official ORE. Distinguish Eligible, Earned, Claimable, Submitted and Paid; Paid requires confirmed transaction evidence. Maintain an auditable reward ledger and unique claim key to prevent duplicate payouts. A user-visible claim fee must be disclosed before approval; a sponsor-funded claim must have enforced sponsor budgets.

Encourage voluntary return with useful progress, receipts, optional check-in reminders and clear session availability. Do not use fabricated scarcity, guilt, misleading earnings, streak loss pressure or endless urgency. Provide reminder controls and a simple off switch.

### ORE Bot and notifications

Add a small ORE Bot bubble and notification inbox using the same graphite/mint components. The bot explains wallet errors, balances, session status, resource limits, receipts, grid selection and next steps. Ground operational answers in authenticated read-only app data and include timestamps. Keep deterministic status/help available without an AI API key; configure an AI provider for enhanced conversation. Unconfigured AI shows Basic help mode.

The bot cannot sign transactions, move funds, start chargeable hosting, change quotas or create guaranteed earning claims. Requests for actions route through normal reviewed controls. Send reminders only after explicit notification consent; allow selected hours, quiet hours, timezone, per-category controls, a daily limit, deduplication and disable/unsubscribe. Use native local/push notifications for Android and supported web push for web; a closed browser cannot run a JavaScript reminder timer reliably. Backend scheduler owns eligibility reminders; notification delivery and provider errors appear in operational logs. Avoid sending wallet addresses or private job data in lock-screen copy.

Notification examples: Your contribution session is available; Your session completed—view the receipt; A reward is claimable—review fees; Your node paused because of its thermal limit. Never say You earned ORE without reward evidence.

### Fully connected ORE Mining and 5×5 grid

Keep this section separate from Edge Node contribution and the sponsored daily campaign. Verify the current official ORE program, SDK/API, account layouts and deployment per cluster before implementing. The official site currently redirects ore.supply to ore.com; available retrieved content did not establish program addresses, grid schema, timing or reward rules. Treat those integration details as unresolved until verified against official source and deployed accounts. Do not implement an older proof-of-work API by assumption or ship fabricated grid data.

Render 25 cells indexed 0–24 in five rows and five columns. Each cell displays verified state supported by the current protocol: allocation, participation and settlement outcome where available. Show current round identifier, source account/program, slot, observation time and connection status. Do not hardcode countdown duration or winners. Subscribe to authoritative chain/program state, validate account owners/layouts and reconcile HTTP snapshots after reconnect. Missing or stale data disables new deployment and retains labelled history. Empty cells are not evidence of no bids unless the source confirms it.

Lite follows the verified protocol's allocation strategy; Pro enables valid cell selection where supported. If these modes are app-defined, describe their exact strategy and costs. Show total SOL, per-round allocation, number of rounds, selected cells, network, fees, maximum authorised spend and relevant protocol risks before signing. MAX reserves transaction fees and respects the user's spend cap. Grid interaction updates the draft; it cannot submit or spend silently.

Build unsigned transactions through a versioned protocol adapter. Simulate, inspect programs/accounts, display exact instruction effects, and hash the exact serialised message. Recompute the message hash before signing; MESSAGE_CHANGED disables approval. Blockhash refresh, fee changes or changed instructions require a new review. Submit the wallet-signed transaction, track confirmation/expiry/failure and reconcile actual chain state; show Pending before confirmation. Automated repeat rounds require an explicit bounded authorisation supported by the actual protocol, with stop/revocation behaviour—never assume a prior signature authorises new transactions.

### Frontend/backend contracts

These are proposed app endpoints, not assertions about the external ORE API. Version them under /api/v1 and publish schemas.

| Endpoint | Contract |
|---|---|
| GET /capabilities | Cluster deployments, provider readiness, wallet/platform capabilities, policy version |
| POST /auth/challenge; POST /auth/verify | Domain-bound nonce sign-in and session creation |
| GET /wallet/balances | Account/cluster-scoped validated balances, slots and freshness |
| POST /node/sessions | Start selected workers with quotas and idempotency key |
| GET /node/sessions/:id | Authoritative state, worker states, expiry and receipt references |
| POST /node/sessions/:id/pause; /stop | Authenticated lifecycle transition and acknowledged outcome |
| GET /telemetry | Source-tagged local-agent/host measurements |
| GET /daily/status; POST /daily/check-in | Server eligibility, usage and unique check-in claim |
| GET /rewards; POST /rewards/:id/claim | Ledger states and idempotent verified payout workflow |
| GET /ore/rounds/current | Verified protocol snapshot and 25-cell state |
| POST /ore/transactions/prepare | Validated draft, simulation, spend summary and message hash |
| POST /ore/transactions/submit | Signed transaction verification, bounded submission and tracking |
| GET /receipts; GET /receipts/:id | Immutable evidence with separate verification result |
| GET/PUT /notifications/preferences | Consent, quiet hours, categories and delivery subscriptions |
| POST /bot/messages | Authenticated grounded help; no signing authority |
| GET /events | Authenticated SSE or WS updates with sequence and resync cursor |

Use persistent storage with uniqueness constraints on starts, claims and payouts. Store money as raw integers/decimal strings, not floating-point values. Authorise every object access, validate inputs, rate-limit expensive operations, keep secrets server-side and scope caches by owner and cluster. Include request IDs and typed errors: WALLET_UNSUPPORTED, NETWORK_MISMATCH, RPC_UNAVAILABLE, TOKEN_UNAVAILABLE, PROVIDER_NOT_CONFIGURED, SESSION_LIMIT, THERMAL_PAUSE, MESSAGE_CHANGED, REWARD_UNFUNDED, GRID_STALE and TRANSACTION_EXPIRED. Keep an audit record of status transitions and confirmation evidence.

### Reusable components and additional icon mapping

Add WalletConnectSheet, NetworkSelector, TokenBalanceCard, DailySessionCard, MiningRing, ResourceCard, WorkerStatusRow, OreAllocationForm, OreGridCell, TransactionReviewSheet, ReceiptCard, BotHelpSheet and NotificationPreferenceRow to the shared system. Native/web implementations may differ while preserving semantics and tokens. Each has loading, empty, stale, unavailable, disabled, error and success variants where relevant.

| New element | Icon | Treatment |
|---|---|---|
| SKR balance | Official SKR asset | Original brand colours |
| Network selector | Connected globe | Mint; visible network text |
| Daily check-in | Calendar with check | Mint; success only after validation |
| Session quota | Clock with progress ring | Mint; textual 0/5 |
| Notification inbox | Rounded bell | Secondary; readable unread count |
| ORE Bot | Rounded chat with small ore/spark mark | Mint; same outline family |
| Cloud host | Cloud with server outline | Mint |
| Grid | 5×5 rounded cells | Mint selected outline; no emoji |
| Claim reward | Gift with check | Mint; disabled when unfunded |
| Worker blocked | Shield with exclamation | Warning plus reason |

### Startup and delivery requirements

Provide pinned dependencies, migrations, configuration validation, example environment files without secrets, repeatable development commands and a readiness page. Configure real RPCs, verified token/program registries, agent/provider connections, notification delivery and campaign funding before marking dependent features Ready. Offline fixtures may exist only in clearly labelled development/test mode. Public RPC read mode and deterministic help should start without paid credentials where possible; production integrations cannot honestly be guaranteed without their dependencies.

Do not ship placeholder handlers that return success, random data, fake balances, dummy grid rounds or local-only reward counters in real mode. Missing dependencies must fail visibly and safely. Deliver source changes, setup instructions and a feature-by-feature verification report. When no source repository is available, deliver this specification and state that code implementation remains pending.

### Acceptance gates

1. Three balances match RPC owner/mint queries; account/network changes never leak previous values; failed reads show errors rather than zero.
2. Compatible tested wallets connect, sign and reconnect on supported platforms; cancellation and unsupported capabilities are handled.
3. All three network selections work for available features; unavailable token/program deployments are clearly blocked.
4. Mine produces one acknowledged backend session; repeated clicks and app restarts cannot create duplicates. Each opted-in worker reports actual start, job state and stop acknowledgement.
5. CPU/memory/storage/network values match platform/agent measurements. No device fixture values reach production.
6. GIF starts only with actual Running state, freezes on pause/failure and respects reduced motion. Decorative particles never alter balances.
7. Five-start daily cap and one-hour expiry survive restarts, simultaneous requests, changed device clocks and network retries.
8. Check-in/reward claims are idempotent, funded, audited and show confirmed payment signatures; unfunded campaigns cannot advertise free ORE.
9. ORE grid matches verified protocol account state for all 25 cells. Stale, disconnected and unsupported-cluster states prevent deployment.
10. ReviewBinding rejects changed messages; user-rejected, expired, failed and confirmed transactions produce correct receipts without duplicate submission.
11. Notification consent, quiet hours, deduplication, disable controls and real delivery work. Bot outputs use live authorised data and cannot execute financial actions.
12. Review all screens in small-phone, large-text, empty, loading, offline and error states. Run relevant unit/integration tests and real-device wallet tests. Report PASS / FAIL / BLOCKED / NOT_RUN with evidence; a mockup or compile success alone cannot establish full functionality.

### Primary references and unresolved integration evidence

- RainbowKit introduction: https://rainbowkit.com/docs/introduction — documented React/wagmi/viem and EIP wallet stack; informs the Solana transport correction.
- Solana Mobile compatible MWA wallets: https://wallets.solanamobile.com/ — capabilities must be tested per wallet/platform.
- MWA integration source: https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/android/docs/integration_guide.md
- SOL balance RPC: https://solana.com/docs/rpc/http/getbalance
- Token owner accounts RPC: https://solana.com/docs/rpc/http/gettokenaccountsbyowner
- ORE official site: https://ore.com/ — current program deployments, account layouts, grid protocol and payout semantics still require source/account verification before coding.

Do not treat this reference list as proof that rewards, providers or protocol integrations are already configured.
