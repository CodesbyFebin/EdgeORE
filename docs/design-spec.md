# EdgeORE — Native Android Design Specification

Version 1.0 · Built by CodesbyFebin · 8 October 2026

## Reference and intent

Reference: the selected four-phone EdgeORE showcase, `image-edit-target-0df98ea8791b7d85.png`. Preserve its graphite surfaces, copper primary actions, mint accents, three-bar E mark and four destinations: Mine, AI, Nodes, Receipts. This specification describes intended native Compose UI. The image is illustrative, not evidence of implementation. Values below are proposed implementation tokens rather than exact pixel measurements sampled from the image.

Tagline: **Your Edge. Your AI. Your Proof.**

## Required copy corrections

- Replace “Run AI. Contribute. Earn ORE.” with **“Review ORE. Control your edge.”** No utility-to-reward multiplier is assumed.
- Keep sample workload graphs in a labeled demo environment. Production charts use actual observations; no NPU label without a measured supported NPU path.
- Replace the sample “120 GB / 1 TB” with measured capacity or **“Capacity unavailable.”**
- A local-model receipt does not establish independent verification. Provider acknowledgement and payment stay separate.
- Concept previews display **“CONCEPT · SAMPLE DATA.”** Production displays concrete capability states rather than a permanent concept badge.

## Color tokens

| Token | Value | Use |
|---|---|---|
| background | `#101414` | App canvas |
| surface | `#1C2323` | Raised cards |
| surfaceInset | `#121919` | Chat and inset areas |
| border | `#3D4745` | Neutral outlines |
| textPrimary | `#EEF2EF` | Headings and body |
| textMuted | `#A6B2AC` | Supporting text |
| mint | `#35E7C0` | Brand, active local controls |
| copper | `#FFA365` | Action and review emphasis |
| copperDeep | `#E86D35` | Decorative gradient endpoint |
| danger | `#FF8275` | Refusal and errors |
| onAction | `#111514` | Text on light copper buttons |

Primary buttons use a subtle copper gradient; sufficient contrast takes priority over matching the artwork. Validate all rendered combinations for accessibility. Status always includes text and an icon, never color alone.

## Typography and layout

Use native sans-serif typography with a geometric bold wordmark. Recommended Compose sizes: screen heading 28sp/32sp; card heading 18sp/24sp; body 16sp/24sp; metadata 14sp/20sp; navigation label 12sp/16sp. Avoid dense tiny copy from the showcase in the installed application. Respect system font scaling; do not clip at 200%.

Base spacing: 4, 8, 12, 16, 24 and 32dp. Horizontal page padding: 16dp on compact phones, 24dp when space permits. Card padding: 16dp. Card radius: 16dp; button radius: 16dp; input radius: 24dp. Borders: 1dp. Touch targets: minimum 48dp. Screen content scrolls above persistent navigation and system insets. Use the actual Android status bar, not a painted fake status bar.

Phone frames, copper hardware bevels, stone background and studio lighting belong to marketing artwork. Do not embed them around the real application UI.

## Shared components

- `EdgeOreHeader`: E mark, wordmark and accessible settings action.
- `CapabilityBadge`: implemented, unavailable, paused, stale or demo, with readable explanation.
- `ObservationCard`: value, units, source and freshness; missing data never defaults to zero.
- `PrimaryAction`: 56dp minimum height, loading and disabled states, single-flight handling.
- `ResourceControl`: label, explanatory text, state and enforcement availability.
- `ReceiptCard`: outcome, time, source and signature availability.
- `EvidenceDimensionsCard`: four independent claim-specific states.
- `EdgeOreNavigation`: Mine, AI, Nodes, Receipts; copper icon and text for selected destination.

Use one consistent outline icon family with approximately 2dp strokes. Never use an earned-reward checkmark merely because a local operation finished.

## Mine page

Order: header → Edge Mode card → operation ring or ORE board → observation cards → workload chart → action → resource controls → navigation.

The reference ring is a pause/control visual. Its states are unavailable, idle, starting, active, pausing, paused and failed. An animated ring requires real activity. For ORE V3 board participation, show the actual supported board and label the workflow **ORE participation**; local hash computation is a distinct benchmark. Do not equate hashes with capital deployment.

Observation cards: Wallet, ORE rewards, Device load. Disconnected wallet shows “Not connected”; unobserved reward shows “Not observed.” Charts show measured series, intervals and gaps; without samples show a compact empty state. Resource controls include charge-only, thermal guard, battery reserve and CPU budget only when enforced. Unimplemented controls are disabled with explanations.

Main action opens the supported workflow review. Demo action says “Preview session.” Production signing is never triggered directly by this dashboard.

## AI page

Order: header → Private AI heading → model and execution card → chat → retention controls → composer → navigation.

Display installed model name, execution location and availability. “Cloud fallback off” describes enforced configuration. Distinguish on-device inference from an owned host receiving the prompt. Model selection is an explicit action; no model means Send is disabled. Loading, streaming, canceled, completed and failed are separate states.

User bubbles use a restrained mint inset; assistant bubbles use neutral graphite. Model output cannot grant permissions or approve a transaction. Show cited local documents only when retrieval is actually implemented. Composer accommodates multiline input and keyboard insets. Announce completion accessibly without flooding TalkBack with every token. Controls allow cancellation and retention selection.

## Nodes page

Order: header → Owned Nodes heading → host card → pairing and permissions → supported operation rows → capacity/bandwidth cards → primary action → navigation.

Unpaired default: “No node paired” and “Pair your node.” Paired cards show host identity, route availability, observation freshness and scopes. Read health, Review logs and Revoke access each invoke their own scoped operation. An offline host shows stale last-known information and pending revocation where applicable.

Storage displays user-provisioned measured capacity, not a universal entitlement. Bandwidth starts off. It cannot be enabled unless destination policy, quotas and consent withdrawal exist. Pairing uses an expiring challenge and authenticated host identity. The page must not offer arbitrary remote shell commands.

## Receipts page

Order: header → Receipts heading → filters → receipt list → selected detail → export action → navigation.

Filters: All, Reviews, Node. Add search when records justify it. Initial state: “No receipts yet.” Rejected records show “No signature”; signed and submitted records expose their distinct outcomes. Detail shows immutable receipt ID, operation ID, cluster, time, source and relevant digest. Append observations without overwriting reviewed bytes.

Evidence dimensions: Local observation, Node signature, Independent verification, Provider acknowledgement. Each dimension identifies what was checked. Payment status remains separate. Missing artifact bytes mean the related integrity claim cannot be verified. Export previews included data and exclusions; successful export is not automatic independent verification.

## Review route

Open as a dedicated route, not a crowded dashboard modal. Display supported action, network, account, destination, amount, fees or uncertainty, program and complete-message binding status. Unknown instructions or changed bytes disable approval. Updating a blockhash requires a new review. Wallet-returned signed bytes must match before applicable broadcast. AI explanations are informative only.

## Accessibility, motion and responsive behavior

Respect safe areas, dark-system bars, keyboard insets and back navigation. Use short optional fades and button-state transitions; reduced motion removes decorative animation. Announce refusal and important state changes. Provide chart summaries and navigable data alternatives. Do not communicate pairing, signing or resource enforcement solely through animation.

On narrow phones, stack observation cards when needed. On tablets, use a navigation rail and optional detail pane while preserving operation policy. Portrait and landscape must retain access to stop/cancel actions. Test large text, screen reader, contrast, keyboard focus, rotation and process recreation.

## Design acceptance checklist

- All four destinations use one shared token/component system.
- No mock production balances, rewards, signatures or telemetry.
- Buttons reach real operations or precise unavailable states.
- Unknown, loading, zero, stale and failed remain distinct.
- No cropped text at large font sizes; minimum targets remain usable.
- Signing is behind review and exact-message policy.
- Local AI and node features identify execution location and permissions.
- Screenshots for submission come from the installed qualified APK.

This document is a design handoff, not a build, device, protocol or store qualification claim.
