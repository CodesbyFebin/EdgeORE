# Hardening-20 pass (branch `feature/hardening-20`, from PR #19 `6c36f95`)

Response to the external "Review & Hardening Guide" (Oct 2026). Status words: **DONE** (in code, machine gates run),
**NOT_RUN** (prepared, needs an owner step or a tool missing here), **BLOCKED** (cannot be done from this repo).
Nothing here is a production-readiness or earnings claim. Device, wallet and devnet gates remain NOT_RUN.

| # | Item | Status | Commit(s) |
|---|---|---|---|
| 1 | `scripts/devnet-deploy.sh` + [DEVNET-RUNBOOK.md](DEVNET-RUNBOOK.md) (deploy + ≤0.01 SOL sample transfer) | DONE on devnet from the build box (program, CLI transfer, checkpoint); in-app transfer NOT_RUN | 4805a42, 1e6bf23, f5b8480 |
| 2 | Device recording (MWA sign → receipt) | NOT_RUN (needs a Seeker) | — |
| 3 | Canonical commit stamp (`scripts/commit-stamp.sh`, `evidence/COMMIT-STAMP.json`, `BuildConfig.SOURCE_STAMP`) | DONE | f0caded, 60a4a92, stamp commit |
| 4 | Dossier README | BLOCKED: dossier repo not found | — |
| 5 | Apache-2.0 `LICENSE` (owner may change it) | DONE | d6ed547 |
| 6 | detekt-cli 1.23.7 + ktlint 1.5.0 jars, baselines, CI job, `scripts/lint-static.sh` | DONE | 8964f88 |
| 7 | `scripts/make-release-keystore.sh` (outside repo, env passwords); no key generated or committed | DONE: build-box key generated outside the repo, signed APK verified (owner should move the key) | 830fb15 |
| 8 | Node-agent SPKI pins; system-CA-only trust anchors (user CAs refused); gateway SPKI pinning confirmed | DONE | 5bbc17f |
| 9 | Dossier EvidenceMatrix links | BLOCKED: dossier repo not found | — |
| 10 | Dossier TrustSpine labels | BLOCKED: dossier repo not found | — |
| 11 | Dossier `metadata.json` Gemini capability | BLOCKED: dossier repo not found | — |
| 12 | MobSF workflow (local container, no secrets) + `scripts/mobsf-scan.sh` | DONE (workflow) / NOT_RUN (docker installed, but the MobSF image pull was interrupted by the user; never run on GitHub yet) | 3275a8f |
| 13 | R8 on for release (verified) + keep rules | DONE | a1e1d8d |
| 14 | FLAG_SECURE: review, receipts, pairing, vault, wallet, QR scan | DONE | d86c926 |
| 15 | BiometricPrompt + device-credential fallback before MWA dispatch (`androidx.biometric:biometric:1.1.0`) | DONE (not exercised on a device) | ea7ea5b |
| 16 | Vault-loss warning at vault creation | DONE | 3c12ec5 |
| 17 | On-chain `ReceiptCheckpoint` + init/record instructions, LiteSVM tests; deployed to devnet; app-side gate: Unknown blocks a checkpoint until a user re-poll | DONE | 9c2bbc2, 8c70dce |
| 18 | Receipt schema v3: device signature binds descriptive fields; v1/v2 still verify | DONE | 9bd6afd, 831bcca |
| 19 | Sealed `BlockhashCheckResult` / `OperationOutcome`; Unknown never auto-resolves | DONE | 545f434 |
| 20 | `dapp-store-metadata.json` template | DONE (template; TODOs remain) | c15e367 |

Notes
- The review's "373 automated tests" summed separate suites. This repo never sums suites; each is reported on its own.
- Item 18: v1/v2 receipts already hashed every body field; v3 additionally names schema, key epoch, kind, outcome,
  title, detail, source, cluster, payment and createdAt in the signed bytes. The receipt checker still verifies the
  integrity of signed receipt contents and reports how many receipts used v3 vs legacy digest-only signatures.
- Item 15 refuses signing on a phone with no screen lock instead of skipping the check.
- Item 8: RPC uses system CAs only; it is not pinned (public RPC providers rotate keys).
- Item 6 baselines: detekt 2,244 and ktlint 16,054 existing findings recorded; any new one fails.
- Item 17: the program's `declare_id!` is unchanged; the local `target/deploy` keypair differs, see DEPLOY-DEVNET.md step 3.
- Gate counts and APK hashes for the stamped commit: `evidence/hardening-20/`.
