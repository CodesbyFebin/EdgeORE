# Appetize hosted-emulator session — d675002bd701

## Runtime

| Field | Value |
|---|---|
| Label | **Hosted Android emulator — interactive APK preview** |
| Provider | Appetize |
| Device | Pixel 7 |
| Android / API | Android 13 (**API 33**) |
| Public link | https://appetize.io/app/l63ubf6tbb4fei2fpezkz5fvla |

This session is a **launch and navigation** check of the installed APK in a hosted emulator. It is **not** wallet authorization, transfer, recovery, or real-transfer receipt evidence.

## Artifact uploaded

| Field | Value |
|---|---|
| File | `EdgeORE-0.2.8-review-d675002bd701-debug.apk` |
| SHA-256 | `6ee17a3cac0c363b130caa2523118e068b3449f9f1d1ac49b449f3dae3f6a4e6` |
| Upload time | 2026-10-09T20:00:05Z (01:30 IST, 10 Oct 2026) |

## Execution timestamp

Screenshots captured **2026-10-09T20:01Z** (≈01:31 IST, 10 Oct 2026) via a browser session against the public Appetize link above. Files in this directory: `01-launch.png` … `07-about.png`, `all-tabs.png`.

## Observed About text

> EdgeORE 0.2.8-review (d675002bd701). Solana devnet only.

Note: the Appetize stream resolution is low; the stamp was read as `d675002bd701`, with individual characters partially blurry in the capture (`07-about.png`).

## Results

| Check | Status | Observation |
|---|---|---|
| Launch | **PASS** | App opened on Mine; no crash or error dialog. Appetize header showed EdgeORE v0.2.8-review. |
| Navigation — Mine | **PASS** | Opened. Wallet Not connected; ring Paused; ORE rewards Not observed. |
| Navigation — AI (Private AI) | **PASS** | Opened. No model names reported; cloud fallback OFF. |
| Navigation — Storage | **PASS** | Opened. Encrypted vault; No vault files yet (≈781.9 MB used shown). |
| Navigation — Nodes | **PASS** | Opened. No node paired. |
| Navigation — Receipts | **PASS** | Opened. No receipts yet. |
| About opened | **PASS** | Stamp text observed as above. |
| Wallet authorization (MWA) | **NOT_RUN** | — |
| Devnet transfer (review → sign → separate submit) | **NOT_RUN** | — |
| Restart recovery around submission | **NOT_RUN** | — |
| Real-transfer receipt verification | **NOT_RUN** | — |

## Observed copy issue

The AI tab shows the label **"On-device execution"** although this build uses an owned Ollama-compatible host (no embedded AI runtime). That label is **not** a capability claim for this candidate. Tracked for a post-submission UI copy fix in [`docs/hardening-backlog.md`](../../docs/hardening-backlog.md) (H5).

## Not included

- Provider dashboard credentials, secret keys, or the unredacted upload response from the host.
- Wallet credentials, seed phrases, or pairing codes.
