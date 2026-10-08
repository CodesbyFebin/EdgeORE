# Qualification report

Recorded after a clean release rebuild. This is not a 10/10 score and not a device session.

## Rebuild

| Check | Result |
|---|---|
| Command | `./gradlew clean assembleRelease` |
| Process exit | 0 |
| Gradle line | `BUILD SUCCESSFUL in 1m 22s` |
| Source at rebuild | `3929edbbd89cfebc8f309b090fd268e532bd684a` |
| Package | `com.edgeore.app` `0.2.6-review` versionCode 8 |
| New APK SHA-256 | `d64d5b92f28ac8856a9904528f464125a3b6333a9b52e1f7b0e622e46196dd0b` |
| Previously recorded SHA-256 | `6f1f73a9eeb58a5dd8f9b305cfa901761a5a8ec9773f2bf5863e7c1b8ee35968` |
| Byte-for-byte match | **No** |
| Certificate SHA-256 | `5baab0630958af7a3a5faabc3f3525f8fd5e2bb6a1b98e19bd6e16b1f6e834a1` (same as the recorded artifact) |
| `classes.dex` SHA-256 | `0a190a196064f12fd0849eae5b3930c5445115eb0441491326ed49fd51e75876` (identical in both APKs) |
| `AndroidManifest.xml` | Identical |
| Zip entries | 161 in both. Uncompressed size 28770140 in both. |

The only file whose bytes differ is `META-INF/version-control-info.textproto`.

- Recorded APK embeds git revision `7433eeec8e368ebf695edebd2401cf54362bbdb6` (HEAD at the time of that build, before the documentation commit).
- This rebuild embeds `3929edbbd89cfebc8f309b090fd268e532bd684a`.

Signing identity and compiled app code match. The outer APK hash does not, because the build stamps the current git revision. A later documentation commit will change that stamp again. Do not treat a future hash mismatch of this one file as an application change.

Gradle still logged the known worker socket error (`Unexpected type tag 71`) and then exited 0. That noise is not a failed compile.

## Not run

No phone, wallet, Ollama host, or node agent was available.

| Item | Status |
|---|---|
| `adb install` and screenshots | NOT_RUN |
| Rotation, large text, process death, no-network, permission denial | NOT_RUN |
| Wallet authorization and devnet balance | NOT_RUN |
| Signed and confirmed transfer | NOT_RUN |
| Receipt from that transfer, including tamper check on a second machine | NOT_RUN |
| Owned-host model reply and public-host refusal on a device | NOT_RUN |
| Airplane-mode inference | BLOCKED. No model runtime is in the APK. |
| Node pair and revoke | NOT_RUN |
| Vault encrypt, export, and delete on a phone | NOT_RUN |
| Demo video and store submission | NOT_RUN |
| ORE, VPN, cloud sync, bandwidth earning | Unavailable on purpose. Not observed. |

Decision remains **NO-GO** for a fully functional or 10/10 claim.
