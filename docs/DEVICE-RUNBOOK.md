# EdgeORE device runbook (main: 0.2.9-review with first-run introduction and QR address entry)

You run this on **your own phone or your own local emulator**. Nothing here has been run on a device by the build agent; every gate below is `NOT_RUN` until you save its evidence. Report a gate as PASS only when its evidence matches the expectation written next to it. A mismatch is a FAIL, and a FAIL is a useful result: save the evidence the same way.

## 0. What you need

From `/workspace/device-kit/` (copy the whole folder to your computer):

| File | What it is |
|---|---|
| `EdgeORE-0.2.9-review-<commit>-debug.apk` | The exact candidate APK. Its commit, sha256, size and About stamp are in `APK-IDENTITY.txt`. |
| `DEVICE-RUNBOOK.md` | This file. |
| `APK-IDENTITY.txt` | Commit, sha256, size, versionName/versionCode and About stamp of the candidate APK. |
| `qr/qr-recipient-address.png` | QR of the bare recipient address `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` (Gates 5a, 5b, 6). |
| `qr/qr-solana-pay-0.01.png` | QR of `solana:73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n?amount=0.01` (fills destination and amount). |
| `qr/qr-refused-spl-token.png` | Negative control: a Solana Pay link with `spl-token=…`, which EdgeORE must refuse. |
| `SHA256SUMS` | Checksums of the files above. |

The `qr/` images are the same files as `docs/device-qr/` in the repository (payloads listed in its README).

The kit does **not** contain a wallet. **You build the mock wallet yourself** (Solana Mobile's reference test wallet; the build agent tested MWA against commit `d444aff0c72d`):

```bash
git clone https://github.com/solana-mobile/mock-mwa-wallet.git && cd mock-mwa-wallet
git checkout d444aff0c72dadd0f5c442ea2bc3559b62916e01      # optional: the commit EdgeORE's MWA path was tested against
echo "privateKey=<BASE58_THROWAWAY_DEVNET_KEY>" >> local.properties   # see its README, "Import a private key"
./gradlew :app:assembleDebug                                  # APK under app/build/outputs/apk/debug/
export MOCK_APK=$(ls "$PWD"/app/build/outputs/apk/debug/*.apk | head -1)
```

**Use a throwaway devnet-only key. Never send it real value, never use it on mainnet, never reuse it anywhere else.** If you skip `privateKey`, the wallet generates a random key on first start; read its address from the wallet screen instead. Record the wallet's address once and use it wherever this runbook says `$SENDER`:

```bash
export SENDER=<your mock wallet's devnet address>
echo "$SENDER" > "$EV/00-sender-address.txt"    # after creating $EV below
```

Fund `$SENDER` from the devnet faucet only (Gate 6 needs a bit more than 0.01 SOL plus a fee).

Also needed: `adb` (Android platform-tools), `curl`, `python3`, and a checkout of this repository at the commit in `APK-IDENTITY.txt` with JDK 17 on `PATH` (for `scripts/verify-receipt.sh`). Gate 9 needs an **arm64** phone (or arm64 emulator image) with at least 6 GB RAM and about 2 GB free storage; on an x86_64 emulator, record Gate 9 as `NOT_RUN (no arm64)`.

### Evidence folder

Create one folder and keep every file named exactly as written in each gate:

```bash
export EV=~/edgeore-device-evidence/$(date +%Y%m%d-%H%M)
mkdir -p "$EV"
cd /path/to/device-kit
```

At the end, the folder holds `00-…` to `10-…` files (including `01a-…`, `05a-…`, `05b-…`) plus `RESULTS.md` (template at the bottom).

Order: 0, 1, **1a**, 2, 3, 4, 5, **5a**, **5b**, 6, 7, 8, 9, 10. Gates 1a, 5a and 5b never prepare, sign or send anything.

---

## Gate 0. Kit integrity and device identity

```bash
sha256sum -c SHA256SUMS | tee "$EV/00-sha256sums-check.txt"
adb devices -l | tee "$EV/00-adb-devices.txt"
{ adb shell getprop ro.product.model; adb shell getprop ro.build.version.release; adb shell getprop ro.build.version.sdk; adb shell getprop ro.product.cpu.abi; adb shell getprop ro.kernel.qemu; } | tee "$EV/00-device-props.txt"
```

PASS: every line in `00-sha256sums-check.txt` ends in `OK`. `00-device-props.txt` tells phone vs emulator (`ro.kernel.qemu` = 1 on an emulator) and the ABI (Gate 9 needs `arm64-v8a`).

## Gate 1. Install both APKs with adb

```bash
APK=$(ls EdgeORE-0.2.9-review-*-debug.apk)
adb install -r "$APK"                                         2>&1 | tee    "$EV/01-install.txt"
adb install -r "$MOCK_APK"                                    2>&1 | tee -a "$EV/01-install.txt"
sha256sum "$MOCK_APK" | tee "$EV/01-mock-wallet-sha256.txt"   # your own build; recorded, not compared
adb shell dumpsys package com.edgeore.app | grep -E "versionName|versionCode" | tee "$EV/01-edgeore-version.txt"
adb shell pm list packages | grep -E "com.edgeore.app|com.solana.mwallet" | tee "$EV/01-packages.txt"
```

If an older EdgeORE is installed with a different signing key, `adb install` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; then `adb uninstall com.edgeore.app` first (this deletes that install's local receipts and vault; export anything you need before).

PASS: both installs print `Success`; `versionName=0.2.9-review`, `versionCode=11`. (The About stamp is checked in Gate 1a, after the introduction.)

## Gate 1a. First-run introduction

This build shows a one-time introduction before the tabs. Use a **fresh install** for this gate: if EdgeORE was installed before, `adb uninstall com.edgeore.app` first (this deletes that install's receipts and vault; export anything you need).

1. Launch EdgeORE. The **Welcome** screen appears instead of the tabs. Scroll through it and screenshot every part: `$EV/01a-intro-1.png`, `01a-intro-2.png`, …
   It must show four cards: **What EdgeORE does**, **What it does not do** (no mining income, ORE rewards, SKR payments or token; no mainnet; not store-ready; never sees your wallet keys), **Devnet only**, and **A wallet is required to sign**.
2. Tap **I understand · continue**. The Mine tab opens with the **Now** card at the top (Wallet · Not connected · tap to connect, Awaiting an outcome, Receipt log, Vault). Screenshot `$EV/01a-now-card.png`.
3. Force-stop and relaunch; the introduction must **not** reappear:
   ```bash
   adb shell am force-stop com.edgeore.app && adb shell monkey -p com.edgeore.app -c android.intent.category.LAUNCHER 1 >/dev/null
   ```
   Screenshot `$EV/01a-relaunch.png`.
4. Open **About this build** (ⓘ, top right) and screenshot it: `$EV/01-about.png`. The dialog also has **Show introduction**; you may tap it to confirm the introduction comes back, then continue again.
5. Optional (TalkBack): with TalkBack on, the card titles are announced as headings. Note it in `RESULTS.md` if you check it.

PASS: the introduction appeared on first launch with the four cards above, did not reappear after relaunch, and the About stamp shows the same commit as `APK-IDENTITY.txt` and no `-dirty` suffix.

## Gate 2. Secure lock screen

Settings → Security (or Security & privacy) → Screen lock → set a **PIN, pattern or password** (not None/Swipe). On an emulator, a PIN works.

```bash
adb shell dumpsys lock_settings 2>/dev/null | grep -iE "CredentialType|quality|Lock screen" | tee "$EV/02-lockscreen.txt"
```

Screenshot the Screen lock settings page to `$EV/02-lockscreen.png`.

PASS: a PIN/pattern/password is set (the screenshot shows it; on most Android versions `02-lockscreen.txt` also shows `CredentialType: PIN`, `PATTERN` or `PASSWORD`; if that dump is empty on your version, the screenshot is the evidence). The mock wallet's authentication and Android Keystore user-auth keys depend on it.

## Gate 3. Start logcat capture (keep running through Gate 8)

In a separate terminal, before Connect:

```bash
adb logcat -c
adb logcat -v threadtime EdgeORE.Wallet:V '*:S' > "$EV/03-logcat-edgeore-wallet.raw.txt"
```

Also capture the full log, used only for the force-stop gate (do **not** share it unfiltered):

```bash
adb logcat -v threadtime > "$EV/03-logcat-full.raw.txt"
```

When you stop the captures (Ctrl-C, after Gate 8), write the shareable filtered copy. The mock wallet logs decrypted test data with the line text `Decrypted information`; those lines are removed:

```bash
grep -v "Decrypted information" "$EV/03-logcat-edgeore-wallet.raw.txt" > "$EV/03-logcat-edgeore-wallet.txt"
grep -c "Decrypted information" "$EV/03-logcat-edgeore-wallet.txt" | tee "$EV/03-decrypted-lines-remaining.txt"   # must print 0
rm "$EV/03-logcat-edgeore-wallet.raw.txt"
```

Keep `03-logcat-full.raw.txt` private; it may contain those lines too.

PASS: `03-logcat-edgeore-wallet.txt` exists, has `EdgeORE.Wallet` lines for the Connect, Disconnect and signing steps, and `03-decrypted-lines-remaining.txt` reads `0`.

## Gate 4. Authenticate in the wallet, then Connect

1. Open **Mock MWA Wallet** (from the launcher). Tap **Authenticate** and pass the lock-screen prompt. Screenshot the `Authentication succeeded!` toast if you can: `$EV/04-wallet-authenticated.png`.
2. Open EdgeORE → Mine → **Connect Solana Wallets** card → **Connect wallet** (the **Now** card's *Wallet* row does the same). The mock wallet shows "EdgeORE wants to connect"; tap **Authorize**.
3. Screenshot EdgeORE showing the connected address: `$EV/04-connected.png`.

PASS: EdgeORE shows your `$SENDER` address (or its short form, first 4 and last 4 characters) as connected, no error text, and the wallet log has a matching authorize line. FAIL evidence: the exact error text EdgeORE shows (the authorization fix exposes it instead of failing silently) plus the screenshot.

## Gate 5. Disconnect

On Mine, tap **Disconnect**. Screenshot to `$EV/05-disconnected.png`.

PASS: the status reads `Disconnected. The wallet confirmed deauthorization.` If it reads `Disconnected in EdgeORE. The wallet did not confirm deauthorization.`, that is a FAIL for wallet confirmation (record it; EdgeORE is still disconnected locally).

Then connect again exactly as in Gate 4 (authenticate in the wallet first), for Gates 5a–6. Screenshot `$EV/05-reconnected.png`.

## Gate 5a. Live camera QR scan (form fill only)

The camera permission must be requested **only** when you tap **Scan QR**, never at install or launch. Open `qr/qr-recipient-address.png` full-screen on your computer (or print it). An emulator's virtual camera usually cannot see it: on an emulator without a webcam passthrough, record `NOT_RUN (emulator camera)` and go to 5b.

1. Before tapping anything, record that the permission is not yet granted:
   ```bash
   adb shell dumpsys package com.edgeore.app | grep -A1 "android.permission.CAMERA" | tee "$EV/05a-camera-before.txt"   # expect granted=false (or no runtime grant yet)
   ```
2. Mine → **Review a supported action**. The destination and amount fields are **empty**. Screenshot `$EV/05a-review-empty.png`.
3. Tap **Scan QR**. Android asks for the camera permission now. First tap **Don't allow**: EdgeORE must stay on the form and show "Camera permission was not granted, so nothing was scanned…". Screenshot `$EV/05a-denied.png`.
4. Tap **Scan QR** again and **Allow** (Android may require you to allow it from app settings after one denial; if so, grant it there and come back). Point the camera at the QR. The scanner closes by itself and the destination field holds `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n`, with a note "Filled from QR: destination 73Cc84…JnR93n…". Screenshot `$EV/05a-filled.png`.
5. Negative control: tap **Scan QR** and scan `qr/qr-refused-spl-token.png`. EdgeORE must show "QR not used: The link asks for an SPL token transfer…" and must not fill an SPL transfer. Screenshot `$EV/05a-refused.png`.
6. Do **not** tap Prepare. Tap **Back**.
   ```bash
   adb shell dumpsys package com.edgeore.app | grep -A1 "android.permission.CAMERA" | tee "$EV/05a-camera-after.txt"   # granted=true
   ```

PASS: no camera prompt before the tap; a denial leaves the form usable with that message; an allowed scan fills exactly the address in the QR; the SPL-token QR is refused with a reason.

## Gate 5b. QR from image (no permission)

1. Push the QR images to the phone's Pictures folder and ask the media scanner to index them:
   ```bash
   adb push qr/qr-solana-pay-0.01.png /sdcard/Pictures/ && adb push qr/qr-recipient-address.png /sdcard/Pictures/
   adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/qr-solana-pay-0.01.png >/dev/null
   adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/qr-recipient-address.png >/dev/null
   ```
   (If the picker does not show them, open the Files or Photos app once, or reboot.)
2. Mine → **Review a supported action** → **QR from image**. The system photo picker opens with **no** permission prompt. Pick `qr-solana-pay-0.01.png`. The destination becomes `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` and the amount `0.01`, with a "Filled from QR … and amount 0.01 SOL" note; the amount field's hint reads `10000000 lamports.` Screenshot `$EV/05b-filled.png`.
3. Do **not** tap Prepare. Tap **Back**.

PASS: the picker opened without a permission prompt and both fields were filled exactly from the QR.

## Gate 6. One approved 0.01 devnet SOL transfer (with the force-stop recovery check)

**Submit exactly once. Never resend, never prepare a second transfer to "retry".** If anything is unclear, stop and observe; observation is read-only.

Before you start, record the balances (devnet RPC, read-only):

```bash
RPC=https://api.devnet.solana.com
for a in "$SENDER" 73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n; do
  curl -s $RPC -H 'content-type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getBalance\",\"params\":[\"$a\"]}"; echo " $a"
done | tee "$EV/06-balances-before.txt"
```

The sender needs more than 0.01 SOL plus a fee; if it does not have it, stop and record `NOT_RUN (unfunded)`. Do not fund it from a wallet holding real value; use the devnet faucet only.

1. Mine → **Review a supported action**. The destination and amount start **empty**. Enter them in one of these ways:
   - type or paste: Destination address `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n`, Amount `0.01`; or
   - **Scan QR** on `qr/qr-solana-pay-0.01.png` (fills both), or **QR from image** with the same file (Gate 5b).

   Either way, read both fields back before going on: the destination field's hint reads "Valid Solana address format…" and the amount's reads `10000000 lamports.`. Do not use **Use my own address (self-transfer)**. Screenshot `$EV/06-form.png`.
2. Tap **Prepare exact-message review**. **Check the review screen before signing:** the **In plain words** card must read "Send 0.01 SOL to 73Cc…R93n" (it is derived from the bytes, but it is a summary). The exact section ("Decoded from the exact bytes") is the authority and must show cluster devnet, fee payer and sender `$SENDER`, recipient `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n`, amount 0.01 SOL (10,000,000 lamports) and a fee. Screenshot every part of it (scroll): `$EV/06-review-1.png`, `06-review-2.png`, … If anything differs, tap **Refuse** and stop.
3. Tap **Approve & sign in wallet**; approve in the mock wallet. EdgeORE must show "Signed but not broadcast". Screenshot `$EV/06-signed.png`.
4. **Force-stop during submit.** Have this command typed in a terminal, unsent:
   ```bash
   adb shell am force-stop com.edgeore.app; date -u +%FT%TZ | tee "$EV/06-force-stop-time.txt"
   ```
   Tap **Submit to devnet** once, then press Enter in the terminal within about a second.
5. Reopen EdgeORE (do not touch Submit yet). Screenshot the Review tab and "Operations awaiting an outcome": `$EV/06-after-restart.png`. One of two things is true:
   - **It may have been sent** (state SUBMITTED/UNKNOWN, "The bytes may have reached devnet. EdgeORE will not resend them…"). There must be **no** Submit button for it. Tap **Observe status (read-only)** until it reports confirmed or failed. This is the expected recovery path.
   - **It was not sent yet** (still "Signed but not broadcast", the stop landed before the broadcast). Check the Explorer link below for the sender first; if no transfer to `73Cc84…R93n` appears, tap **Submit to devnet** once: these are the same signed bytes, sent for the first time, not a resend. If the bytes expired instead, tap **Discard unsent bytes** and stop; record that the recovery path kept nothing counted twice, and do not prepare another transfer.

   Note which case happened in `RESULTS.md`.
6. When EdgeORE shows the signature, copy it (long-press/select, or read it off the screen) and save it:
   ```bash
   SIG=<paste signature>
   echo "$SIG" > "$EV/06-signature.txt"
   curl -s $RPC -H 'content-type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getSignatureStatuses\",\"params\":[[\"$SIG\"],{\"searchTransactionHistory\":true}]}" | tee "$EV/06-getSignatureStatuses.json"
   curl -s $RPC -H 'content-type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getTransaction\",\"params\":[\"$SIG\",{\"encoding\":\"jsonParsed\",\"maxSupportedTransactionVersion\":0}]}" > "$EV/06-getTransaction.json"
   echo "https://explorer.solana.com/tx/$SIG?cluster=devnet" | tee "$EV/06-explorer-url.txt"
   ```
   Open the Explorer URL and save a screenshot: `$EV/06-explorer.png`. Re-run the balance loop into `$EV/06-balances-after.txt`.
7. Screenshot EdgeORE's final state for the operation: `$EV/06-final.png`.

PASS: `06-getSignatureStatuses.json` has a non-null value with `"err":null` and `confirmationStatus` `confirmed` or `finalized`; Explorer shows exactly one 0.01 SOL transfer from `$SENDER` to `73Cc84…R93n` for this signature; the recipient balance rose by 10,000,000 lamports; after the force-stop, EdgeORE never offered to resend bytes that may have been sent, and the operation is counted once. If `value` is `[null]`, keep observing; "not found" is not proof it never landed.

## Gate 7. Export the real receipt and verify it offline (PASS original, FAIL lamports+1)

1. Receipts tab → open the receipt for the Gate 6 transfer → **Export this receipt** → in the share sheet you may cancel; the file is already written to the app's cache. Screenshot the preview: `$EV/07-export-preview.png`.
2. Pull the newest export (the debug APK allows `run-as`):
   ```bash
   F=$(adb exec-out run-as com.edgeore.app sh -c 'ls -t cache/exports | head -1' | tr -d '\r')
   adb exec-out run-as com.edgeore.app cat "cache/exports/$F" > "$EV/07-receipt.json"
   sha256sum "$EV/07-receipt.json" | tee "$EV/07-receipt.sha256"
   ```
3. Make the lamports+1 copy. This changes one digit inside the signed receipt body and nothing else:
   ```bash
   python3 - "$EV/07-receipt.json" "$EV/07-receipt-lamports-plus-1.json" <<'PY'
   import json, re, sys
   doc = json.load(open(sys.argv[1]))
   for r in doc["receipts"]:
       m = re.search(r'"lamports":(\d+)', r["body"])
       if m:
           n = int(m.group(1)); r["body"] = r["body"].replace(m.group(0), f'"lamports":{n + 1}', 1)
           print(f"lamports {n} -> {n + 1}"); break
   else:
       sys.exit("no receipt with integer lamports found; export the transfer receipt")
   json.dump(doc, open(sys.argv[2], "w"), indent=2)
   PY
   ```
4. Verify both, from the repository checkout at the APK's commit:
   ```bash
   cd /path/to/EdgeORE   # git checkout <commit from APK-IDENTITY.txt>
   scripts/verify-receipt.sh "$EV/07-receipt.json" > "$EV/07-verify-original.txt" 2>"$EV/07-verify-original.stderr"; echo "exit=$?" | tee -a "$EV/07-verify-original.txt"
   scripts/verify-receipt.sh "$EV/07-receipt-lamports-plus-1.json" > "$EV/07-verify-lamports-plus-1.txt" 2>"$EV/07-verify-lamports-plus-1.stderr"; echo "exit=$?" | tee -a "$EV/07-verify-lamports-plus-1.txt"
   ```

PASS: the original prints an integrity-OK line, `Descriptive export fields covered by the signed envelope`, and `exit=0`; the lamports+1 copy is rejected with a digest/signature finding and `exit=1`. Exit `2` means the checker itself failed to build or run (not a verification result): fix the toolchain and re-run. This proves the bytes were not changed since export on this device; it does not by itself prove the chain state (Gate 6 does that).

## Gate 8. Android Keystore check

1. **About this build** → the line `Receipts signed with: …` must read `Android Keystore P-256 · key <16 hex>`. Screenshot `$EV/08-about-keystore.png`. If it reads `Software key in app storage (Keystore unavailable; weaker)`, that is a FAIL.
2. From the Gate 7 export:
   ```bash
   python3 -c "import json,sys; d=json.load(open(sys.argv[1])); [print(k['keyId'][:16], k['protection'], k['firstUsedAt']) for k in d['deviceKeys']]" "$EV/07-receipt.json" | tee "$EV/08-export-key-protection.txt"
   ```
   PASS: the current key's protection is `Android Keystore P-256` and its keyId prefix matches the About screen.
3. Storage tab: add a small file to the vault, tap **Verify integrity**, screenshot `$EV/08-vault-verify.png` (card text: "AES-256-GCM. The key stays in this phone's Android Keystore."). PASS: verify reports the file intact.
4. Confirm the app's private files are not readable from the shell user (the debug `run-as` is the only route):
   ```bash
   adb shell ls /data/data/com.edgeore.app 2>&1 | tee "$EV/08-private-dir-shell.txt"   # expect: Permission denied
   ```

## Gate 9. On-device AI: one model download, then an airplane-mode run (arm64 only)

Only `Qwen2.5-1.5B-Instruct (q8)` (1,597,931,520 bytes, not gated) is downloadable without a Hugging Face account; the allowlist lists 6 GB device RAM for it. Use Wi-Fi.

1. AI tab → the execution card title reads `Owned-host AI · on-device not set up`. Screenshot `$EV/09-ai-before.png`.
2. Select **Qwen2.5-1.5B-Instruct (q8)** → **Download 1.6 GB…** → **Download** in the consent dialog. Wait for `… downloaded. Size and SHA-256 matched the allowlist.` Tap **Verify checksum**. Screenshot `$EV/09-downloaded.png`. Title must now read `On-device model downloaded, not loaded`.
   ```bash
   adb exec-out run-as com.edgeore.app sh -c 'find files -name "*.litertlm" -exec ls -l {} \;' | tee "$EV/09-model-file.txt"   # expect one file of 1597931520 bytes
   ```
3. Enable **airplane mode** (Wi-Fi and mobile data off). Record it:
   ```bash
   adb shell settings get global airplane_mode_on | tee "$EV/09-airplane.txt"        # 1
   adb shell dumpsys connectivity | grep -iE "Active default network|NetworkAgentInfo" | head -5 | tee -a "$EV/09-airplane.txt"
   ```
4. Type a short prompt (for example `Name three primary colours.`) and tap **Run on this phone**. Wait for the reply. Screenshot `$EV/09-offline-reply.png`; the title must read `On-device model loaded`. Optionally tap **Run airplane-mode test** and include its text; that check reports the network state only, not the on-device run.
5. Turn airplane mode off afterwards.

PASS: a reply was generated with airplane mode on (`airplane_mode_on` = 1, no active default network), on `arm64-v8a`. A load failure (for example out of memory) is a FAIL with the exact error text and the RAM figure the app shows. On x86_64, record `NOT_RUN (no arm64)`.

## Gate 10. Close out

Stop both logcat captures and run the filter in Gate 3. Then:

```bash
adb shell dumpsys package com.edgeore.app | grep -E "versionName|versionCode|lastUpdateTime" > "$EV/10-final-package.txt"
(cd "$EV" && sha256sum $(ls | grep -v SHA256SUMS) > SHA256SUMS)
```

Do not include `03-logcat-full.raw.txt` when you share the folder.

### `RESULTS.md` template (save as `$EV/RESULTS.md`)

```markdown
APK commit / sha256 (from APK-IDENTITY.txt):
Mock wallet: commit you built, sha256 (01-mock-wallet-sha256.txt), sender address ($SENDER):
Device: model, Android version, ABI, phone or emulator:
| Gate | Result (PASS / FAIL / NOT_RUN + reason) | Evidence files |
|---|---|---|
| 0 Kit integrity | | 00-* |
| 1 Install | | 01-install.txt, 01-edgeore-version.txt, 01-packages.txt, 01-mock-wallet-sha256.txt |
| 1a First-run introduction + About stamp | | 01a-*, 01-about.png |
| 2 Secure lock screen | | 02-* |
| 3 Logcat EdgeORE.Wallet (filtered) | | 03-logcat-edgeore-wallet.txt |
| 4 Authenticate + Connect | | 04-* |
| 5 Disconnect (wallet confirmed?) | | 05-* |
| 5a Live camera QR scan (prompt only on tap, deny path, fill, SPL refusal) | | 05a-* |
| 5b QR from image (no prompt, fills destination + amount) | | 05b-* |
| 6 0.01 SOL transfer, signature, status, Explorer | | 06-* |
| 6 Force-stop recovery (which case?) | | 06-force-stop-time.txt, 06-after-restart.png |
| 7 Receipt verify: original exit / lamports+1 exit | | 07-* |
| 8 Keystore | | 08-* |
| 9 On-device AI offline (arm64) | | 09-* |
Notes (anything unexpected, exact error texts):
```
