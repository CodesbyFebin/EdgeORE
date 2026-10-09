# Known limitations (`0.2.8-review`)

Current status is in [`qualification-status.md`](qualification-status.md). Nothing below is a device result.

**Device and wallet**
- No phone was attached. Install, rotation, process death on a device, TalkBack and wallet return are NOT_RUN.
- No wallet authorized this build. No devnet transaction was broadcast, so no signature is recorded.
- The `0.2.8-review` APK is debug-signed. If the installed EdgeORE was signed with a different certificate (for example the `0.2.6-review` release build, certificate `5baab063…`), Android refuses the upgrade (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). **Do not uninstall `0.2.6-review` to get past this.** Its vault, receipt and node keys are bound to that install's Android Keystore; uninstalling destroys them and the data cannot be recovered. Use a different phone or an emulator for this build. A debug build installed over an earlier debug build of the same key upgrades in place.

**Transfers**
- Recovery after a restart only reads chain state. It never resends. A transfer whose outcome is unknown stays visible, and its amount stays counted, until the chain reports it confirmed, finalized or failed. Blockhash expiry alone does not settle it (see "Expiry policy" in `qualification-status.md`).
- If a write to the operation store fails, memory is kept equal to disk and wallet actions (review, sign, send, discard) are refused until EdgeORE restarts. The Review screen shows the storage error.
- If the operation store is damaged, transfers are disabled. The app does not treat a damaged store as empty.
- Signed bytes that were never sent can be discarded, but the wallet's signature still exists. Expiry of the blockhash is what makes them harmless. Signing re-reads the block height first: if it cannot be read, signing is refused; if the blockhash is expired or within 20 blocks of expiry, a fresh review is required.

**AI**
- Cancel closes the local socket. Ollama's non-streaming API does not acknowledge the cancel, so the host may finish the work anyway. The app labels the result "Stopped receiving".
- A private IP is not proof that you own the host. Every resolved address must be private, and the request goes to the address that was checked.
- Model checksums match a digest that you type in. That is not a trusted provenance pin.
- No on-device runtime or weights are in the APK.

**Controls**
- Each control on screen is labelled **Enforced**, **Saved only** or **Unavailable**. The CPU limit, model memory limit, sharing quota and sharing consent are Saved only. The kill switch and location are Unavailable. The battery, thermal and charge gates are Enforced, but only on the Edge Mode state: no mining workload exists.
- Resume on Mine changes a local flag. It does not start a miner.

**Telemetry**
- CPU and disk rates need two samples. A missing sample or a counter reset is shown as absent, not as zero or a spike.
- Traffic since boot is device traffic, not bandwidth that EdgeORE contributed. Shared bytes stay at 0.

**Storage**
- Vault export writes plaintext. The Keystore key never leaves the phone, so uninstalling or a reset makes the vault unrecoverable.

**Receipts**
- The device signature shows that this install wrote the record. The wallet's Ed25519 signature is verified separately.
- An export that hides the device key verifies only against keys pinned on the same install.

**Scope**
- ORE rewards are not observed. SKR is not CPU-mined. VPN, cloud sync and bandwidth earning are not implemented.

**Screenshots**
- The images in `docs/screenshots/` are Robolectric renders, not phone captures.
