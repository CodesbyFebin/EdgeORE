# EdgeORE — Pitch Package (retrospective + dApp Store launch)

> **Framing.** Solana Mobile hackathon submissions closed **8 Oct 2026**; this package is dated **12 Oct 2026**. It is a retrospective plus a Solana dApp Store launch package, **not** a live submission.
> Every claim carries one label: **QUALIFIED** (re-run with evidence in this repo) · **IMPLEMENTED_UNVERIFIED** (in source, evidence only from a dev machine or JVM) · **DESIGN** (written down, not built) · **BLOCKED** · **NOT_RUN**.
> Evidence base: commit `84a788cbd926` on `feature/ore-grid-readonly`, gate folder [`evidence/build-84a788cbd926/`](../../evidence/build-84a788cbd926/).

## 1. One-liner

**Every wallet action on Seeker produces a signed receipt you can verify offline — including the ones you refuse.**

## 2. Problem — five Seeker-user pain points

1. You tap Approve in a wallet popup and never see the exact bytes you signed.
2. After a timeout you don't know whether the transaction sent, failed or is still pending — and the app may resend.
3. There is no artifact on your phone that answers "what did I sign, and when".
4. Two apps give you two different stories about the same transaction.
5. "Success" is a UI label, not a proof you can hand to anyone.

## 3. Solution — the trust spine

One invariant across every action: **a receipt you own.**

```
Review bytes → Approve exact message → Sign in wallet → Verify returned bytes → Submit separately → Export receipt
```

If the wallet returns different bytes, the app refuses. If the network is unreachable after signing, the app observes — it does not auto-resend. Unknown outcomes stay unknown until observation resolves them. The standalone JVM checker **verifies the integrity of signed receipt contents**; it does not prove that a transaction happened on-chain.

Status: wallet journey (devnet SOL transfer) **IMPLEMENTED_UNVERIFIED**; end-to-end run with a real wallet **NOT_RUN**.

## 4. Product tour

| Screen | What the user does | What they see | Status |
|---|---|---|---|
| Mine | Taps **Start Mining** to run a contribution session: Groth16 proof jobs on a paired node + 256 KiB vault-sealed chunk round trips through the owned gateway | Phase (IDLE → STARTING → ACTIVE → STOPPING), measured numbers, receipts; pickaxe animates only while ACTIVE | IMPLEMENTED_UNVERIFIED (dev-machine e2e: 5 proof jobs + 5 round trips verified, receipt checker ok=6) |
| ORE board (from Mine) | Opens a read-only view of the live ORE v3 board | 5×5 grid, round, per-square SOL, motherlode, text-only advisor | IMPLEMENTED_UNVERIFIED (JVM tests + mainnet read captures; no device run) |
| Private AI | Configures an owned-host model endpoint or downloads an allowlisted on-device model | Endpoint policy, checksum-pinned allowlist | IMPLEMENTED_UNVERIFIED |
| Storage | Encrypts a 256 KiB chunk through the owned gateway | Round trip, byte-identical verify | IMPLEMENTED_UNVERIFIED (dev machine) |
| Nodes | Pairs a pinned node-agent revision | Scoped pairing, health read, log read, revoke | IMPLEMENTED_UNVERIFIED |
| Receipts | Exports a signed record | JSON envelope, sha256, signature | QUALIFIED at JVM checker level (14 tests); IMPLEMENTED_UNVERIFIED on device |

Nothing in any screen earns anything: no ORE, SKR, points or payment.

## 5. SKR

| Mechanic | Role of SKR | Status |
|---|---|---|
| Burn-to-mine (epoch pool) | SKR committed to a pool → points | DESIGN |
| Pro-Mine stake | Stake SKR → multiplier | DESIGN |
| Daily chests | SKR paid for refreshes | DESIGN |
| Weekly / monthly draws | SKR as qualifier | DESIGN — paid-chance mechanics would need odds disclosure, geofencing and legal review before any build |
| Epoch airdrop pool | Reward at epoch close | DESIGN — no pool size set, nothing allocated, nothing on-chain |

**Live right now: nothing.** The app does **not** read SKR mint state; the only SKR references in `app/src/main` are disclaimers ("no ORE, SKR, points or payment").

## 6. ORE

| Mechanic | Role of ORE | Status |
|---|---|---|
| ORE v3 board read | Reads Board + Round accounts (25 squares, per-square SOL, motherlode) over public mainnet RPC | IMPLEMENTED_UNVERIFIED (JVM fixtures from real mainnet captures; no device run) |
| Advisor | Explains the snapshot in plain text, quoting only snapshot values; not financial advice | IMPLEMENTED_UNVERIFIED |
| Deploy / checkpoint / claim | — | Not built by choice. The app cannot build or sign any ORE instruction. |
| ORE rewards for contribution | — | Not offered. No integration with the ORE program beyond reading accounts. |

Layouts and program id come from [regolith-labs/ore](https://github.com/regolith-labs/ore) at `48c203bd75db3cc45105ec29d8f8db719e5a2263` (see [`docs/ore/README.md`](../ore/README.md)). EdgeORE is not affiliated with Regolith Labs.

## 7. Evidence (84a788cbd926) — counts listed separately, never summed

| Suite | Result | Source |
|---|---|---|
| Android JVM unit tests | 330 tests: 325 passed, 0 failed, 5 skipped (42 suites) | `summary.md`, `junit/` |
| Android lint (debug) | No issues | `lint-debug.txt` |
| Receipt checker (JVM) | 14 passed | `separate-gates/receipt-checker.log` |
| Storage gateway (Python) | 17 passed | `separate-gates/gateway-tests.log` |
| Extension kit | 22 passed | `separate-gates/kit-tests.log` |
| Build-policy script tests | 12 passed | `separate-gates/policy-tests.log` |
| Release-manifest checker tests | 9 passed | `separate-gates/check-manifest-tests.log` |
| Release manifest check | PASS | `release-manifest.log` |
| Build policy | PASS | `separate-gates/policy.log` |
| Contribution session e2e (dev machine) | 1 test passed; 5 proof jobs + 5 gateway round trips verified; checker accepted 6 receipts | `session-e2e/` |
| Screenshot suite | verifyRoborazziDebug exit 0 | `screens-verify.log` |
| Debug APK | `0.2.9-review` / versionCode 11, sha256 `9a101821892e5946c9aac32f68988ebd2ecb050fe6c5eb500db5feb1156f5222`, debug-signed | `summary.md` |
| Physical Seeker, wallet run, devnet transfer, demo video | NOT_RUN | — |

Platform facts (from source): `minSdk 26`, `targetSdk 35`. No Google Play Services, Firebase, analytics, crash-reporting or tracking SDKs in the dependency list. The app **does** request `CAMERA` (optional feature) for QR address scanning, driven by platform Camera2 + ZXing core — no camera SDK. Wallet keys stay in the wallet app via Mobile Wallet Adapter; there is **no direct Seed Vault integration**.

## 8. Roadmap

| Milestone | Content | Status |
|---|---|---|
| Now | Device gates on a Seeker: wallet journey, devnet transfer, session, receipts, video | NOT_RUN |
| Hardening | Detekt, ktlint, MobSF, release signing, bundletool, dApp Store listing ([backlog](../hardening-backlog.md)) | DESIGN |
| v0.3 | SKR mechanics only after legal review, on devnet | DESIGN |
| Later | BLE / mesh / WebRTC / libp2p transports | DESIGN (deferred) |

## 9. 90-second demo script

| Time | Shot |
|---|---|
| 0–10 s | One-liner over the Mine screen idle state |
| 10–30 s | Start Mining → ACTIVE, measured numbers tick, pickaxe swings; Stop |
| 30–45 s | Receipts → export; run the JVM checker on a laptop: accepted |
| 45–60 s | Edit one byte of the export → checker rejects |
| 60–75 s | Review a devnet transfer: exact bytes, sign in wallet, submit separately |
| 75–90 s | ORE board read-only + advisor note; close on "no earnings, no deploy, receipts you own" |

Status: script DESIGN; video NOT_RUN.

## 10. Positioning

Not a wallet, not a miner, not a game. EdgeORE is a **receipt layer** for a Seeker owner's own actions and infrastructure. Generic fit: consumer mobile security, DePIN tooling on owned hardware, Solana Mobile dApp Store utility. (No specific hackathon tracks are claimed: none are documented in this repo.)

## 11. Q&A

- **Does it earn ORE or SKR?** No. No mining income, rewards or token of any kind.
- **Is it production-ready?** No. Debug-signed review candidate; device gates NOT_RUN.
- **What does the checker prove?** It verifies the integrity of signed receipt contents. It does not prove an on-chain outcome.
- **Seed Vault?** Not integrated directly; signing goes through Mobile Wallet Adapter to the user's wallet.
- **Mainnet?** Transactions are devnet only. Mainnet is used only for read-only ORE account reads.
- **Why a camera permission?** QR address entry; optional feature.

## 12. dApp Store pre-submit checklist

- [ ] Release build signed with the owner's key (not in repo) — BLOCKED on key
- [ ] AAB/APK validated with bundletool — NOT_RUN
- [ ] MobSF static scan clean — NOT_RUN
- [ ] Detekt + ktlint in CI — DESIGN
- [ ] Physical Seeker walkthrough + video — NOT_RUN
- [ ] dApp Store publisher NFT/listing, privacy policy, store assets — NOT_RUN
- [ ] LICENSE file — **missing** (repo has NOTICE only); owner to choose
- [x] Release manifest check PASS · lint clean · receipt checker 14/14

## 13. Credits and attribution

Built by CodesbyFebin. Upstream: regolith-labs/ore (account layouts), Solana Mobile Wallet Adapter, Google AI Edge Gallery / LiteRT-LM, ZXing — see [`NOTICE`](../../NOTICE).
