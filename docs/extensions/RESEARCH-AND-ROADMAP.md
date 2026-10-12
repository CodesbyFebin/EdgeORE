# EdgeORE: evidence-first edge workspace

Research-backed extension candidate. This is a product direction and offline tooling, not a claim
that the Android application has executed these new functions. No private account, wallet pop-up,
mailbox, chat transcript, token page or signed-in dashboard from the supplied tab list was accessed.
Only relevant public primary-source documentation and repositories were selected. Directory listings
and vendor reward claims are discovery material, not proof of integration or economics.

## The distinctive product

EdgeORE can stand out by making one question easy to answer: what was allowed, where did data go,
what actually happened, and was anything paid? Those questions must remain separate. A visual
activity ring, a signed record and a provider upload acknowledgement cannot answer all four.

Keep the existing five destinations and exact-byte Review route. Improve the product through five
coherent functions rather than adding unrelated blockchains, mining buttons or automatic wallet agents.

### 1. Capability Passport

Each runtime/provider should declare its actual capabilities, limits, version and observation time.
A downloaded SDK or model enum is not an executable capability. The owned gateway now exposes an
authenticated /v1/capabilities route with maximum object size, retained payload quota and explicit
false values for pinning and portable restore. It does not claim settlement integration.

Future Android work: show capability cards with implementation status and separately recorded runtime
qualification. Carry APK/host digest and evidence reference into each observed result. Stale or missing
capabilities cannot enable work. RunAnywhere's capability discovery is the architectural reference,
but its SDK has not been imported and its conditional license must be reviewed before any adoption.

### 2. Consent Capsules

Bind workload, destination, data categories, byte/time budgets, retention, expiration and revocation
identifier into deterministic bytes. The new Python prototype implements a deliberately narrow
version-1 format with strict fields and integer ranges. It computes a digest and performs a bounded
review preflight. Unknown fields and malformed values are refused rather than silently ignored.

The planner checks expiration, an externally supplied revocation flag, capability availability and
qualification assertions, workload matching, requested budgets and an explicit HTTPS-origin allowlist.
An on-device request never falls back to a network origin. It returns ELIGIBLE_FOR_REVIEW, never
"authorized", "signed" or "executed". These assertions still need authenticated runtime evidence.
Retention, byte metering and revocation must be enforced in Android and the backend before this
becomes a real contribution consent mechanism. Do not treat a digest as a user's signature.

Future Android work: a readable review sheet showing exact destination and data categories, plus a
single-session grant. Preserve reviewed canonical bytes in private durable state. Runtime adapters
must check the same grant before and during work; exceeding limits must terminate or pause work and
record an honest partial outcome. Model downloads and workload consent remain separate approvals.

### 3. Portable Evidence Desk

The existing receipt checker verifies the integrity of signed receipt contents. A new supplemental
artifact checker validates explicitly listed files against a bounded manifest using streamed SHA-256.
It rejects traversal, duplicate paths, symlinks, malformed hashes and size mismatches. It is useful
for handing a reviewer the exact APK/log/screenshot set without making them trust a prose summary.

Important boundary: the manifest is unsigned. Editing both an artifact and its manifest can pass
content matching. The source commit and environment fields are labels, not independently established
facts. Author identity, workload correctness and settlement stay NOT_VERIFIED. Integrate this tool
with actual signed provenance or independently fetched source records later; do not call it proof of
compute. Keep evidence directories private and stable while checking them.

Future Android work: an explicit export preview with file selection, size, cluster, artifact identity
and excluded secrets. Separate display sections for key protection, execution observation and payment.
Do not export raw user documents by default. Desktop companion work may borrow the paired-device
interaction idea from CrossPaste, but no clipboard surveillance or CrossPaste source is included.

### 4. Recoverable Ciphertext Storage

Use the existing durable BackupCoordinator and optional owned gateway adapter. The backend has real
idempotent writes, authenticated retrieval and SQLite persistence. This iteration adds transactional
retained-payload quotas and explicit capability discovery. Canceled data still consumes quota because
cancellation is not deletion. Quota values limit stored payload, not total SQLite/journal disk usage;
a filesystem quota and reverse-proxy limits remain required.

Titan and owned IPFS remain replaceable provider candidates. Neither is connected by this patch.
An acknowledged upload means the backend accepted ciphertext, not a retrieval guarantee, lifetime
storage or SOL reward. The current device-bound backup remains undecryptable after loss of its
Keystore key. Portable passphrase-based backup is a separate reviewed crypto implementation.

Future work: portable authenticated backup envelope, bounded encrypted manifest, explicit key-loss
warnings, interruption checkpoints and a clean second-device restore rehearsal. Optional multi-provider
replication must meter real copies and independent retrieval attempts before showing availability.
The archived maidsafe/autonomi repository is not a current implementation source; follow its official
migration notice to WithAutonomi if that provider is explored.

### 5. Bounded Work, Separately Observed Settlement

Retain Android WorkManager's opt-in scheduling and constraints. Charging or Wi-Fi being present
isn't permission to run an unqualified job. Thermal, battery and bandwidth observations must be
actual readings. The current contribution scheduler still has no registered qualified workload.
This extension does not change that fact.

First future job: bounded local inference or a deterministic document transform, with exact input
and model digests, explicit consent and externally attributable output checks. Record local execution
separately from verifier acceptance. Hardware-backed signing protects keys; it does not attest
arbitrary computation. Do not add slashing or reputation based on self-reported job counts.

Retain the existing Anchor settlement prototype. No program was deployed and no payment was sent.
A SOL payout card needs a real signature, named cluster, accepted program identity and observed
lamport movement. Phantom development tools inform test/debug procedures; they do not replace the
native MWA review/sign/separate-submit contract or authorize agents to approve wallet prompts.

## Selected sources and decisions

| Source from supplied URLs | Observed content | Adaptation |
|---|---|---|
| RunanywhereAI/runanywhere-sdks | Capability discovery and multiple local runtime backends; custom conditional license | Capability Passport pattern; no SDK vendoring |
| CrossPaste/crosspaste-desktop | Paired multi-device desktop experience; AGPL-3.0 | Explicit companion handoff design only; no copied code |
| pojntfx/gon2n | n2n edge/supernode daemons and privileged network-interface setup | Separate host lifecycle; no Android VPN or open proxy added |
| maidsafe/autonomi | Archived with migration notice to WithAutonomi | Provider abstraction only; do not pin archived network code |
| Solana Mobile test-with-any-Android-device | Mock MWA testing and secure lock-screen requirement | Preserve named test runtime and wallet authentication preconditions |
| Android WorkManager define-work guide | Work constraints, retries and scheduling limits | Retain existing scheduler; no always-running mobile agent claim |
| Phantom developer-powertools overview | Validation, testnet/debug and go-live resources | Test checklist references; no wallet SDK replacement |
| Anchor documentation | Solana program tooling | Retain existing program/testing boundary; deployment remains gated |
| Edge Network developer resources | Provider developer tooling | Future adapter research only; no API integration claimed |
| GitHub Well-Architected resources | Repository/engineering guidance | Focused extension docs, separate CI, review templates and evidence |

Direct source links:
- https://github.com/RunanywhereAI/runanywhere-sdks and its LICENSE
- https://github.com/CrossPaste/crosspaste-desktop
- https://github.com/pojntfx/gon2n
- https://github.com/maidsafe/autonomi
- https://docs.solanamobile.com/recipes/general/test-with-any-android-device
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://docs.phantom.com/developer-powertools/overview
- https://www.anchor-lang.com/docs
- https://edge.network/developers
- https://learn.github.com/well-architected/

## Implementation sequence and qualification

1. This patch: offline consent/evidence kit, actual gateway quota/discovery, documentation and tests.
2. Compile and test the previous Kotlin gateway adapter on a network-enabled Android build host.
3. Integrate capability/consent UI and durable enforcement only after preserving existing runtime gates.
4. Qualify one owned provider and portable backup with failure/cancel/restore tests.
5. Qualify one useful bounded workload before any customer-funded job integration.
6. Deploy and observe devnet settlement only with specific owner approval.
7. Add the actual project license, signed release, store disclosures and clean-install evidence.

Do not score the candidate "10/10" or advertise features using this roadmap as evidence. These tools
are original EdgeORE prototype code inspired by the linked public sources; no vendor endorsement
or upstream security audit is implied. Stars are a result of usefulness and distribution, not a gate.
