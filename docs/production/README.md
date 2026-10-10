# Production kit files (imported unchanged)

These files come from the owner's **EdgeORE Master Production Kit** (`EdgeORE-Master-Production-Kit.zip`, sha256 `8447bbc9479f856c2f0271f048a962585b7c50dc207c70ecd8f40b01ce2d2569`, 692 files under `EdgeORE/`). They were added to `main` byte-for-byte; none of their text was edited.

| Path on `main` | sha256 | Source |
|---|---|---|
| `AGENTS.md` | `7e4f3625872c62d3d25aaf77ca2b3de28b1f19fc2b904ee2c7379dec6464b735` | kit |
| `design.md` | `f1800dc51c879c59a2437f33331581ccf5899c30af38b97ad9685f9d1569d369` | kit (production interface spec v2) |
| `SOURCE-PROVENANCE.json` | `2eeefb9cc76cc07aab02458d0b5ae369a644c476cbcb74947888ee565960ac02` | kit |
| `START-HERE.md` | `d33f37408963ab47680ea075352d5c84c0acfc1639822b8772d14eb98248493d` | kit |
| `MASTER-BUILD-PROMPT.md` | `60ff09158730a4f143905b173bdb3a339d00744b6fdff5225ef0c2171ba6e073` | kit |
| `docs/production/GATES.json` | see `FILE-HASHES.json` | kit |
| `docs/production/EVIDENCE-TEMPLATE.md` | see `FILE-HASHES.json` | kit |
| `docs/production/FILE-HASHES.json` | — | kit (hashes of the kit snapshot) |
| `docs/production/ui-preview.html` | `3982a25020403b9aa0cb44e8f6e5af7049ba4a92ad7b79f9bd1670ddc8d0341a` | kit (`EdgeORE-UI-Preview.html` in the owner's SHA256 list) |
| `docs/production/master-blueprint-2026-10-09.md` | `78ded760ac5a71cc5bad01cba30346c99f21d46c44fff46027d0f94b0d9842f1` | separate attachment, "EdgeORE master blueprint and Google AI Studio build prompt", 9 Oct 2026 |

`KIT-VALIDATION.json` (named in the owner's SHA256 list) was not inside the zip or attached, so it is not here.

## Provenance: `main` is newer than the kit's base

- The kit's tracked source is exactly `main` at `6f7b60050f3651227ddc9d7dd261f6d31f7324b2` plus the ten files above minus the blueprint (checked with `diff -r` against `git archive 6f7b600`: no file changed or removed). Against the tested commit `250be029b35c` the only other differences are files that later docs merges added to `main` unchanged, and the README hero image from `6f7b600`. **The kit changes no app source, Gradle or resource file.**
- Since then `main` gained PR #11 (`4e70d28`, Clearance UX port: first-run introduction, Now card, empty review fields, Scan QR / QR from image, plain-language card, accessibility, ZXing core 3.5.3) and PR #12 (`3b95157`, device runbook Gates 1a/5a/5b, QR test images, gate evidence on `3c2b2ac1378e`).
- So `SOURCE-PROVENANCE.json` (`source_commit` `6f7b600`, `tested_source_commit` `250be029b35c`) and `FILE-HASHES.json` describe the **kit snapshot**, not current `main`. `FILE-HASHES.json` will not match files changed after `6f7b600` (for example `README.md`, `docs/DEVICE-RUNBOOK.md`, the Review screen). The current tested commit and APK are in `docs/HANDOFF-GOOGLE-AI-STUDIO.md`.
- `START-HERE.md` says the kit files "are delivered in this ZIP and are not pushed upstream"; that was true of the ZIP and is no longer true now that they are on `main`.

Review notes on these files (claims against evidence and the project rules) are in `docs/HANDOFF-GOOGLE-AI-STUDIO.md`, section "Production kit import notes".
