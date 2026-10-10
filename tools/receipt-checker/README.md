# JVM receipt checker

Evidence tooling only. Compiles the existing Android app's Kotlin ReceiptVerifier and helpers unchanged on Java 17. Requires Java 17 and network access for the first Gradle/dependency bootstrap, but no Android SDK. Existing dependency versions are reused.

From the repository root:

```bash
bash gradlew -p tools/receipt-checker --no-daemon test installDist
bash scripts/verify-receipt.sh /absolute/path/original.json
bash scripts/verify-receipt.sh /absolute/path/tampered-copy.json
```

Exit 0 means the verifier accepted integrity; exit 1 means rejection; exit 2 means invocation/read/build error and no acceptance. An unpinned export is explicitly reported as self-contained integrity, not trusted provenance. Offline verification does not establish chain confirmation, physical truth or payment.

Optionally pin a device public key obtained through a separate trusted channel:

```bash
bash scripts/verify-receipt.sh original.json --trusted-key device-spki.der
```

The key file must contain binary DER SubjectPublicKeyInfo, not PEM or a Solana wallet address. Keys copied only from the same untrusted export do not establish provenance.

After successful bootstrap, run entirely offline without Gradle:

```bash
tools/receipt-checker/build/install/edgeore-receipt-checker/bin/edgeore-receipt-checker original.json
```

Keep the original export. Change an authenticated body field in a copy, preserving valid JSON, then verify both files. The seven CLI tests use synthetic policy receipts and do not substitute for a real transfer export or second-machine run.

Qualification status at authoring: shell syntax checked; JVM tests/build blocked before compilation by Gradle download `Network is unreachable`.

Later run (fresh clone of the PR #3 head, JDK 17.0.20.1, Gradle 8.9): `bash gradlew -p tools/receipt-checker --no-daemon test installDist` built and ran 7 tests, 7 passed. End to end on a generated full-chain export (2 receipts, one carrying a wallet Ed25519 signature): original `VERIFIED: PASS` exit 0; copy with one body byte changed `VERIFIED: FAIL` (`SHA-256 does not match body`) exit 1; truncated copy exit 1. After the build, `scripts/verify-receipt.sh` also passed with networking removed. One file per call. Limits: the export's descriptive fields (`note`, `exclusions`, `payment`, `location`, top-level `exportedAt`) are not signed, so edits there are not detected; no real-transfer export has been checked. App sources are unchanged.

Every run also prints `Not covered by any signature (descriptive only): top-level note, exclusions, payment, location, exportedAt; deviceKeys[].protection, deviceKeys[].firstUsedAt`. Editing those fields is not detected (hardening backlog H4).
