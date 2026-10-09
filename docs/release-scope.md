# Release scope

> **Historical (0.2.6-review).** This page describes the frozen `0.2.6-review` candidate and is kept for the record. The current candidate is `0.2.8-review`; see [qualification-status.md](qualification-status.md).

Primary journey, not yet executed on a phone:

Authorize a real wallet → observe its devnet account → review a System Program transfer → sign the exact reviewed message → submit on purpose → observe confirmed or finalized → export a receipt and verify it on a second process.

Included in the candidate as code:

- Mine, AI, Storage, Nodes, Receipts. Settings live inside those screens, not as a separate qualified destination.
- Devnet System Program transfer review, exact-message check, separate submit.
- Owned-host Ollama-compatible chat. Public hosts are refused.
- AES-256-GCM vault bound to Android Keystore.
- Device storage, traffic-since-boot, battery, and second-sample CPU and disk readings.
- Tamper-evident receipt log.

Excluded until its own gate passes:

- ORE deploy, checkpoint, or claim.
- SKR transfers.
- VPN tunnel.
- Cloud sync.
- Bandwidth sharing or bandwidth-to-ORE rewards.
- On-device model inference.
- dApp Store publishing.

Navigation order in the app: Mine, AI, Storage, Nodes, Receipts.
