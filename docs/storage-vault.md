# Storage page: encrypted local vault and optional device-bound encrypted backup

Branch `feature/storage-vault`. JVM-tested only. Android runtime, Android Keystore, remote upload and restore are **NOT_RUN**.

## What ships

| Part | Status |
|---|---|
| Storage screen (`ui/screens/StorageVault.kt`, top of `StorageScreen.kt`): title, vault usage, configured allowance, available device space, Import file, file list with separate local and remote-backup states, export / backup status / delete, empty state, Remote backup card | Implemented. JVM renders only (`docs/screenshots/storage/`) |
| Import through the Storage Access Framework (`OpenDocument`), with the byte limit enforced while reading (per-file cap, or the allowance remaining if smaller) whatever length the provider reports | Implemented, JVM-tested |
| Existing EOV2 envelope, Android Keystore AES-256-GCM key, atomic publication (temp file, fsync, rename, directory fsync) | Unchanged format; `read` now goes through `LocalVault.openEnvelope`, the one authentication/decryption path also used by restore |
| Export of a decrypted copy only after a confirmation that says the copy is no longer protected by the vault | Implemented |
| Delete with confirmation that names the local copy and says a remote copy is not deleted by this action | Implemented |
| Receipts: `ReceiptKind.STORAGE` for import, decrypted export, local delete, and (when a backend exists) backup/restore events | Implemented. Receipts carry operation id, vault object id, SHA-256 of the **encrypted** object and observations. No file name, plaintext, plaintext digest, credential or recovery secret |
| Remote backup | **Not configured.** No EdgeORE backend exists in this repository. The app uses `NotConfiguredBackupProvider`, which refuses every remote action. Nothing is uploaded and no upload is simulated |

Receipts are app-record integrity evidence. They do not prove that a provider stored anything permanently, or that a deleted file was physically erased.

## Device-bound encrypted backup (contract only)

Intended route: Android → authenticated EdgeORE backend → owner's IPFS node. Only `BackupProvider` (in `storage/Backup.kt`) and the client state machine (`BackupCoordinator`) exist.

- **Ciphertext only.** `LocalVault.sealForBackup` re-seals the object with the same device-bound Keystore key in the same EOV2 format, with an empty name field, so the file name never leaves the phone. `UploadRequest` has no filename, MIME type or plaintext-digest field.
- **Device-bound.** Only this installation's Keystore key can decrypt a backup. Cross-device recovery is **not implemented**; this change adds no portable key format and no key export.
- **IPFS content is addressable by anyone who has its CID.** Ciphertext and its length are therefore treated as public.
- Job state is written (`BackupJobStore`, atomic replace) **before** each network call. A damaged job store fails closed.
- After a restart, jobs left in `QUEUED`, `UPLOADING` or `CANCEL_REQUESTED` become `OUTCOME_UNKNOWN` without network access. `reconcile` records exactly what the backend reports.
- `UPLOAD_ACKNOWLEDGED` is not a pin. `PINNED` is recorded only when the backend reports that the configured node acknowledged pinning, with a matching ciphertext digest. A remote reference or CID on its own never counts as availability.
- Cancellation is `CANCELED` only when the backend acknowledges it; otherwise `OUTCOME_UNKNOWN`.

### Restore verification

1. Download into a size-capped temporary file (`BoundedFileSink`, limit = recorded ciphertext length).
2. Check length and SHA-256 against the values recorded at backup time → `DOWNLOADED`.
3. Decrypt through `LocalVault.openEnvelope`, the same EOV2 AAD check as a local read → `DECRYPTED`.
4. Compare the plaintext SHA-256 and length with the values recorded in the app-private job store at backup time.
5. Publish as a **new** vault object ("<name> (restored)") through the normal atomic `put`. The original is never overwritten → `RESTORE_VERIFIED`.

The UI shows each checkpoint separately: upload acknowledged, pinned on configured node, downloaded, successfully decrypted, restore verified. An acknowledgement or pin never maps to "Restore verified" (`StorageLabels.remoteState`).

### What a backend must provide before this can be configured

Not implemented here. Recorded so the client contract is clear:

- Authenticate the phone. Keep auth secrets out of source, logs and receipts.
- Keep IPFS credentials and the IPFS administrative API on the server. The app never talks to an IPFS admin endpoint.
- Enforce request and response size limits, timeouts and per-user storage quotas.
- Make `upload` idempotent per client job id. Expose `status`, bounded `download` and `cancel`.
- Report `Pinned` only after the configured node acknowledges the pin, and echo the ciphertext SHA-256.
- Never put private filenames in public provider metadata.

## Failure handling (import/export)

| Case | Result |
|---|---|
| Provider gives no length, a wrong length, or an endless stream | Read stops at limit + 1 byte; "File is larger than …" or allowance refusal; nothing saved |
| Allowance exceeded | `OverAllowance`; nothing saved |
| Phone storage full (pre-check or `ENOSPC` during the write) | `NoSpace`; temp file removed |
| Coroutine canceled before publication | Temp file removed in `AtomicFiles.write`; "Import canceled. Nothing was saved." |
| Permission revoked / document gone / provider I/O error | `SourceUnavailable`; nothing saved |
| Keystore key unavailable | `KeyUnavailable` |
| Tampered, truncated or foreign object, or wrong key | `Tampered` / `Corrupt`; export writes nothing |
| Unreadable header | Listed with local state "Unavailable", never hidden |

The file list is refreshed from disk only after `put` returns (published). The success message is set in the same refresh.

## Tests (JVM, software AES key stands in for Keystore)

- `StorageVaultTest`: 10 tests (import limits, quota, disk full, permission loss, cancellation, publish-before-list, key unavailable, tamper/truncate/wrong key on export, unreadable header, local delete).
- `BackupCoordinatorTest`: 13 tests against a **test double** backend in the test file only (unavailable provider, ciphertext-only upload, persisted-before-network and restart reconciliation, failed upload, digest mismatch, cancellation, acknowledgement ≠ restore verified, verified restore as a new object, corrupted/truncated/oversized download, wrong/unavailable key, plaintext digest mismatch, damaged job store, JSON round trip).
- `screens/StorageRenderTest` (only with `-Pscreens`): JVM renders, not device screenshots.

None of these show that Android Keystore, SAF providers on a phone, or any backend behaves this way. Those stay NOT_RUN.
