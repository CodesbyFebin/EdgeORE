# Qualification record: feature/kotlin-dapp

> **Historical (feature/kotlin-dapp, 2026-10-08).** Current results: [`docs/qualification-status.md`](../docs/qualification-status.md).

Recorded 2026-10-08 05:08 IST on a Debian 13 x86_64 build box using Temurin JDK 17.0.20.1, Gradle 8.9 (official wrapper), AGP 8.7.3, Kotlin 2.0.21 and Android SDK platform 35.

## Run and passed

| Gate | Command | Result |
|---|---|---|
| Unit tests | `./gradlew :app:testDebugUnitTest` | **58 passed, 0 failed, 1 skipped.** The skipped test is the real-agent test, which needs `EDGEORE_NODE_IT`; see [unit-tests.txt](unit-tests.txt). |
| Lint | `./gradlew :app:lintDebug` | **No issues found**: 0 errors, 0 warnings. Lint initially flagged `BigInteger.longValueExact` (API 31) as a crash on API 26–30; this is fixed. |
| Assemble | `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` | **Success.** `app-debug.apk` is 14,884,239 bytes. |
| Real node agent (JVM) | `DEPROOF_NODE=… bash scripts/node-agent-it.sh` | **Pass.** Run against the Go `deproof-node` built from DeProof--EdgeORE@7431f08: wrong pin refused, wrong code refused, pair, replay refused, observe (fingerprint matches), forged key refused, out-of-scope action refused, revoke, then refused after revoke. See [node-agent-it.log](node-agent-it.log). |

The full build log is in [build-gate.log](build-gate.log).

## Not run (no claim made)

- **Instrumented tests** (`app/src/androidTest`) compile but have **not been run**. On the build box, the x86_64 emulator never came online: KVM was reported usable, but qemu idled and adb stayed offline.
- **Android TLS with the node agent's Ed25519 certificate.** It works on the JVM. Android has not been tested.
- **Mobile Wallet Adapter round-trip** (connect, then `signTransactions`) has not been run with a wallet app. No devnet transaction has been submitted.
- **Owned-host AI** has not been run against a live Ollama-compatible server.
- **Physical-device behaviour** (thermal and battery readings, Keystore, share sheet, large fonts, TalkBack) has not been checked.
- **ORE, SKR, storage and bandwidth** are not implemented.
