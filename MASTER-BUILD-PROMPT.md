# EdgeORE master production build prompt

Paste this entire prompt into an agent with this repository checkout and an Android build environment.

You are implementing EdgeORE for CodesbyFebin. Read `AGENTS.md`, `design.md`, `SOURCE-PROVENANCE.json`, `docs/HANDOFF-GOOGLE-AI-STUDIO.md`, `docs/DEVICE-RUNBOOK.md`, `docs/SETTLEMENT-SPEC.md`, `docs/DEPLOY-DEVNET.md`, existing tests and evidence before editing. Work from an isolated branch. This is a production progression request, not permission to manufacture a 10/10 claim, deploy a program, spend funds, sign a transaction, upload private data or publish a release.

## Objective

Deliver a native Kotlin/Compose app implementing the exact UI contract in `design.md` and closing the phases in `docs/production/GATES.json`. Keep existing exact-message review, durable compare-and-set storage, fail-closed damaged stores, signature checks, unknown-outcome reservations and observation-only recovery. Reuse existing code; do not replace it with generic SDK samples. Preserve upstream notices and source provenance.

## Establish the baseline

1. Read the exact checked-out commit and working-tree state. Distinguish kit base, tested source `250be029b35c`, and every future tested commit.
2. Inspect Gradle dependencies, ABI/model allowlist, manifests, receipt format and Anchor program. Do not trust stale README descriptions. Record tool versions and dependency comparison.
3. Run each applicable suite separately. Do not add totals together: Android, screenshots, manifest policy, receipt checker, LiteSVM, local validator. Preserve commands, exit codes, XML and logs. Existing handoff results are historical; they are not new results from your machine.
4. Discover device access. If no usable runtime exists, complete all independent implementation and verification work and report only the blocked device gates. Never ask an operator for repeated adb commands when hosted access is the chosen path. Hosted multi-app availability and wallet auth must be verified, not assumed.

## Phase V: close the current candidate's runtime gates first

Make no broad Android refactor before reproducing/triaging current candidate behavior. Verify APK SHA-256, signing certificate, version and About stamp; capture environment. Set secure screen lock for the test wallet. Use a generated throwaway devnet-only wallet, never an embedded shared private key. Connect, explicitly disconnect, restart and reconnect. Preserve SDK failures with stage/code/time; RPC failure must not erase authorization.

Prepare a bounded devnet transfer for separate human review. Stop before signing or submitting unless specifically authorized for that exact transfer. Approval for an old session is not blanket approval for future transfers. After authorization, persist before send; record signature/status/RPC. Interrupt during submission and prove restart recovery observes without resending. Export that real receipt; original must verify; change signed-body `lamports` in a copy and require exit 1. Keep original. Test local vault and on-device generation on supported arm64 hardware. Preserve logs and redacted video. Mark unsupported hardware gates BLOCKED, never PASS.

## Phase H: owned infrastructure

Test the pinned real node agent: challenge expiry/replay, independent TLS pin confirmation, scoped health/log reads, revoke acknowledgement and offline pending state. Test owned-host AI model discovery, document bounds, response, cancel and refusal of public endpoints. Proposed H gate definitions in this kit are new acceptance criteria; they are not legacy runbook gates 11a–c. Specify trusted network/data flow clearly.

## Phase B: portable backup

Design and implement a separate versioned, authenticated, passphrase-wrapped backup envelope without exporting local Keystore keys. Review KDF parameters, salt/nonce rules, bounded parsing, path traversal defenses, manifest integrity and rollback policy. Choose an explicitly owned/configured storage backend with owner input; do not silently choose a public provider. Implement interruption/cancel, quota and retrieval verification. Demonstrate restore on a clean second device with only passphrase and backup; wrong passphrase/corruption must fail before publishing files. Delete/wipe only a disposable test profile with explicit approval.

## Phase P: consent

Canonicalize consent payloads with version, workload, host, data categories, byte/time/charge limits, retention, expiration and revocation. Save exact bytes/hash. Enforce limits in the scheduler and remote protocol, not merely in copy. No qualified workload means no work. Proposed P criteria are newly defined, not invented old gates 13a–b.

## Phase J: settlement

Re-run current Anchor tests before changes. Verify spec v2 canonical instructions/layouts and deterministic proof checks. Prepare devnet deployment with genesis guard, exact build hash, program/authority identity and cost estimate. Stop for owner approval before deploy; keys remain outside repository. After approved deployment, verify fetched deployed bytes and authority. Implement Android client/IDL integration with exact-message review and durable operations. Run funded success once, duplicate-proof refusal, verifier rejection/refund and deadline refund/ambiguous-timeout cases. Observe actual lamports separately from receipt integrity. No mainnet enablement without a separate audited release decision.

## Phase T: trust labels

Separate Key protection, Execution verification and Settlement state. Hardware attestation proves a key's attributes, not arbitrary AI computation. Compute reputation only from externally attributable job outcomes. Show source and freshness; self-reported capacity stays self-reported. Do not introduce staking/slashing, mesh, federated learning or rewards under this release without a reviewed requirement and evidence plan.

## Phase R: release

Implement exact UI layout, all states, accessibility and visual matrix in `design.md`. Preserve functional contracts when extracting view-model features. Bind any new receipt-envelope-required policy cryptographically in a versioned checkpoint; retain legacy verification semantics. Qualify signed-content and unsigned-field coverage accurately.

Have the owner choose a LICENSE and provide release signing through private environment configuration. Never commit keystores/passwords. Build non-debuggable release; strip preview/test activities; verify merged manifest and certificate with available Android tools. Review data safety, model licenses, network permissions, backup retention, vulnerabilities and dependency changes. Run clean-clone checks against the final tested commit. Build a release manifest with full hashes, versions, signer identity, device environment and evidence links. Create a reviewable draft release and store listing; publish only after explicit approval.

## Definition of done

A new user can install the exact shipped signed artifact, understand each data flow, connect a wallet, review/sign/submit safely, recover unknown outcomes without resend, verify signed receipt contents outside the app, use each advertised feature and find evidence for each claim. Advertised portable backup must restore on a separate clean device. Advertised SOL settlement must be observed on its named cluster with exact payout evidence. Physical-device claims need physical-device evidence. A release score is a reviewed assessment, never a target rewritten into a result.

## Required deliverables

- Focused commits and final diff; updated design and architecture decisions.
- Per-phase gate ledger with status, tested commit, APK digest, environment, UTC, command, exit code and evidence links.
- Each suite's own summary, plus device and deployed-program outcomes separately.
- Signed candidate APK/AAB when owner key is available; SHA256SUMS; certificate identity; source ZIP excluding secrets/caches; clone/build instructions.
- README/status/wiki/store copy matching actual evidence; known limitations and rollback instructions.
- Final report: implemented, measured, blocked, decisions needed. If production gates remain open, call it a review candidate and enumerate them. Do not invent success, models, balances, payouts, signed bytes, screenshots or audit results.
