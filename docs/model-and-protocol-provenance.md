# Model and protocol provenance

## Solana

- Cluster: devnet, set in `WalletCoordinator`.
- Supported instruction: System Program transfer built in `SolanaMessage`.
- Approval checks the reviewed message bytes, fee payer, and Ed25519 signature in `TransferReview`.
- ORE program id, instruction layout, and mint were not pinned into a qualified client in this candidate. ORE remains not observed.

## Wallet

- Library: Mobile Wallet Adapter client `2.0.3`.
- The app receives an account and signed bytes. It does not hold the seed.

## AI

- Client: Ollama-compatible `/api/tags` and `/api/chat` in `OwnedHostModelClient`.
- No model weights are in the APK.
- On-device runtime: LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:0.8.0` (Apache-2.0) behind the Java-only `:ondevice-llm` module. Allowlist entries come from Google AI Edge Gallery `model_allowlists/1_0_20.json` at `a8e7956`, pinned to Hugging Face commits with SHA-256. Not run on a device. See `docs/on-device-ai.md`.
- No model name, digest, or license was qualified against a running server in this session.

## Node agent

- Protocol tests exist. The live agent test is skipped when the Go agent is absent. That skip is NOT_RUN, not PASS.

## Vault

- AES-256-GCM, 12-byte nonce from the cipher init, key alias `edgeore.vault` in Android Keystore.
- Nonce reuse and cross-device restore were not executed on hardware.
