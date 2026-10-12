# EdgeORE — Solana Mobile Judge Review

**Your Edge. Your AI. Your Proof.**

Review an exact Solana message, sign in your wallet, submit separately, and keep a portable receipt whose signed contents can be checked outside the app.

**Current status:** native Android **review candidate**. Wallet transactions are **Devnet only**. Physical Seeker and end-to-end wallet gates remain **NOT_RUN**. No mining income, rewards, token issuance or production-readiness claim.

[Repository](https://github.com/CodesbyFebin/EdgeORE) · [Design and app changes — draft PR #20](https://github.com/CodesbyFebin/EdgeORE/pull/20) · [Recorded checks](https://github.com/CodesbyFebin/EdgeORE/blob/feature/mint-holographic-design/evidence/mint-holographic/RESULTS.md) · [Implementation boundaries](https://github.com/CodesbyFebin/EdgeORE/blob/feature/mint-holographic-design/docs/design/mint-implementation-report.md) · [Pitch deck](https://github.com/CodesbyFebin/EdgeORE/blob/main/docs/pitch/EdgeORE-deck.pdf)

## Start here — three-minute review

1. **Understand the problem.** A wallet signature, a network timeout and an app's success label are different observations. EdgeORE preserves the reviewed bytes, submission attempt and later status so an unknown result cannot silently become a resend.
2. **Inspect the native product.** Mine, AI, Storage, Nodes and Receipts share a mint holographic design. A dedicated Review route controls signing. The latest refinement is in draft PR #20; it is not merged or device-qualified.
3. **Check the evidence.** Open the recorded gate table, JUnit results and artifact identity. Build checks, JVM renders and device results are kept separate.
4. **Read the open gates.** Wallet authorization → reviewed Devnet transfer → separate submit → confirmed observation → interrupted-send recovery → real receipt export and external verification remain the next device session.

## What is distinctive

| Decision | Why it matters | Current boundary |
|---|---|---|
| Bind review to exact serialized message bytes | A wallet-returned message must match the approved bytes; signatures are checked | Source and JVM regression coverage; latest device run NOT_RUN |
| Separate signing from submission | A signature alone is never presented as a broadcast | Devnet System Program transfer path |
| Persist an attempt before sending | Crash recovery observes status rather than automatically sending again | Durable operation ledger; latest interrupted-send device gate NOT_RUN |
| Portable receipt checker | Another machine can verify the integrity of signed receipt contents | Does not independently confirm chain outcome, work or payment |
| Owned infrastructure | Scoped node pairing, encrypted storage and explicitly configured AI hosts | Phone/owned-host integration gates still open |

## Recorded evidence — counts are separate

Tested implementation: `164f04e4b846`. Evidence: `evidence/mint-holographic/RESULTS.md` on the draft PR branch. Later documentation commits do not become new tested source results.

| Check | Recorded result | Scope |
|---|---|---|
| Android JVM regression suite | **336 tests: 331 passed, 5 skipped, 0 failures/errors** | Build host; skipped cases need external services or optional capture |
| Android lint + APK assembly | **PASS** | Debug review candidate; not release signing |
| Screenshots and UI interactions | **29/29 PASS** | Robolectric, including wallet sheet, settings, basic help and 200% text navigation |
| Standalone receipt checker | **14/14 PASS** | Separate JVM project |
| Build-policy tests | **12/12 PASS** | Dependency declaration/pinning checks |
| Manifest self-tests and inspection | **9/9 PASS; manifest PASS** | Separate policy check |
| Physical Seeker / real transfer / real receipt / video | **NOT_RUN** | No device success implied |

APK SHA-256: `1b832497f3454b00b75ed290187a208b5984d3f9fc738e8dcf688d13429992a8`. Build stamp: `164f04e4b846-dirty` (intentional screenshot/evidence refresh; see gate report). Debug-signed, package `com.edgeore.app`.

## Product boundaries

- **Wallet:** native Solana Mobile Wallet Adapter; keys stay in the wallet. No direct Seed Vault integration claim. Wallet-specific capability tests remain open.
- **Balances:** public SOL observations on Devnet/Testnet/Mainnet; pinned mainnet ORE mint reader. The network selector is read-only; signing remains Devnet. SKR registry is not verified. Actual balance matching on this candidate is unqualified.
- **ORE:** live-source 5×5 mainnet observer exists. It is **read-only**; no deploy/checkpoint/claim or earning path.
- **Mine:** the configured owner-node proof and storage-gateway session only. Bandwidth relay and cloud hosting require providers; not all workers are running.
- **Daily rewards:** no funded campaign or authoritative daily eligibility server. No free ORE, fabricated 0/5 quota or paid check-in claim.
- **AI:** existing owned-host and on-device paths, plus deterministic basic ORE Bot help. Bot help cannot sign, move funds or create earning claims.
- **Notifications:** opt-in local Android help reminders are implemented. Actual device delivery and any backend inbox/push remain unqualified or unconfigured.
- **Licensing:** public source is available; the project's LICENSE remains an owner decision. Upstream notices are retained. Do not describe an absent license as MIT.

## Artifacts and demonstrations

| Artifact | Availability |
|---|---|
| Latest source, design contract and evidence | Draft PR #20 and its branch |
| Latest APK identity | Recorded in the evidence; debug candidate, not a store release. The existing Android CI artifact is configured to include review APKs after a successful run. |
| Latest UI screenshots | JVM renders; see below |
| Historical hosted Android preview | [Appetize](https://appetize.io/app/l63ubf6tbb4fei2fpezkz5fvla), **older `d675002bd701`**: launch/navigation only; not the mint refinement or wallet proof |
| Pitch deck | Linked above; distinguish pitch/roadmap from device evidence |
| Installed-app demo video | NOT_RUN / not supplied |
| Confirmed transfer, Explorer signature, real-transfer receipt | NOT_RUN / not supplied |

<details>
<summary>Latest app appearance — JVM render, not a device capture</summary>

<img width="330" alt="EdgeORE Mine screen: mint interface, disconnected wallet and unavailable balances; JVM render" src="https://raw.githubusercontent.com/CodesbyFebin/EdgeORE/feature/mint-holographic-design/docs/screenshots/01-mine.png" />

</details>

## Board guide

**Status** means work progress. **Evidence gate** means qualification. “Done” never converts a device gate into PASS.

Read the numbered judge cards first, then the runtime blockers. Each evidence card links its source and names its environment. Submission review, device qualification, post-win launch and research remain separate scopes.

**Hackathon framing:** repository pitch materials record submissions closed on **8 October 2026**. This is a judge-review/retrospective workspace, not a claim of a new live submission. dApp Store publication remains a separate post-win milestone; it is not an already-completed artifact or a submission gate.

Built by [CodesbyFebin](https://github.com/CodesbyFebin).
