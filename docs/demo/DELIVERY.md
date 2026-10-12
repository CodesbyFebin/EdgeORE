# Delivered review artifacts

Prepared on 2026-10-12. This is a debug review candidate, not a fully wired production release.

- [APK + video + manifest package](https://d2ol7oe51mr4n9.cloudfront.net/user_3Jn4WZsssIDmoyWlzqAT3djW3wG/cc6b3407-f20a-4e9b-9c06-4e314fc3b4ec.zip)
- [78-second UI preview](https://d2ol7oe51mr4n9.cloudfront.net/user_3Jn4WZsssIDmoyWlzqAT3djW3wG/204269b0-c2bf-4334-b882-57eee497d776.mp4)
- [Matching CI build and evidence](https://github.com/CodesbyFebin/EdgeORE/actions/runs/38249756791)
- [Source review](https://github.com/CodesbyFebin/EdgeORE/pull/22)

The ZIP includes `EdgeORE-debug.apk`, the MP4, CI build summary and a manifest. Both exports returned HTTP 200 and were confirmed by the media service. The video is an H.264 1080×1920 native Higgsedit export, 78 seconds, 3,811,010 bytes. Encoded Mine and ORE frames were visually inspected. No generative app footage, balances, receipts, wallet success or active session was inserted.

## Identity and provenance

| Artifact | Identity |
|---|---|
| Base merged mint source | `6ba12a50e42d4d120cf085537354b5226e73175d` |
| Tested delivery head | `ecb7edfac31647f8b1f618cfbb185c1007187d30` |
| CI synthetic merge | `4cb3db3b693a4f0bbc8890b3fa44d3ba1e9f0aa3` |
| APK in the package | SHA-256 `1766c963e5d59e0627849a7e7b08f2333be11f51ecdf2ece54e48d6ec5db2054` |
| Version | `0.1.0-clockin`, code 12, debug-signed |
| Video source | Fresh API 28 Robolectric renders at `f212551`; verified on `ecb7edfac316` |
| Video | SHA-256 `686af6905f6ba27917a44b3cbc2bdab7cae4333bf126a50ef41792e4ab4e931e` |
| Locally built APK (not the packaged CI APK) | SHA-256 `3db19fbbec90ae691899e8e38145e523289dcccb8077a33d142480db48b6e899`, stamp `ecb7edfac316` |

Different build stamps and ephemeral debug certificates mean the local and CI APK hashes differ. The package's own manifest and CI summary describe the shipped review binary. Later changes to this file and the evidence directory are documentation/evidence only.

## Fresh checks

Local Android command: `bash gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, exit 0: **361 tests, 354 passed, 7 skipped, 0 failures/errors**. Record and verify screenshot commands with `-Pscreens` both exited 0: **25/25**. Logs and JUnit XML are in `evidence/demo-delivery/`. The linked CI run passed all five jobs, including the standalone receipt checker and unsigned R8 build; those remain separate suites.

## Video timeline

| Seconds | Rendered content |
|---|---|
| 0–7 | Onboarding and capability boundaries |
| 7–14 | Mine, wallet, assets and check-in |
| 14–21 | Paused owned-resource session |
| 21–28 | Availability-gated ORE deployment |
| 28–35 | AI configuration state |
| 35–42 | Storage vault |
| 42–49 | Export-warning dialog |
| 49–56 | Nodes configuration state |
| 56–63 | Empty receipts |
| 63–70 | Transaction review |
| 70–78 | Remaining qualification gates |

Every shot visibly says **JVM UI PREVIEW · DEVICE GATES NOT_RUN**. Mine detail shots crop the actual full-page JVM render; they do not represent device scrolling. Settings, interactive loading transitions, pickaxe motion, real wallet/provider actions and a complete installed-app tour are not demonstrated by this preview.

## Runtime limits

No physical phone or KVM was available. Android 15 software emulation did not reach a usable app runtime. Android 9 installed an earlier branch APK and rendered onboarding, but repeated System UI freezes prevented a usable tour. That is not a qualification result for the merged candidate delivered here. Revyl returned no matching plugin in discovery; Simulator Login, Test Android Apps and Ansight exposed no callable tools. Hugging Face was listed as installed but provided no Android runtime capability in this session.

The existing native MWA and token registry remain. Actual wallet signing, confirmation, receipt export, notifications, owned-provider device sessions and physical Seeker gates remain NOT_RUN. ORE deployment requires its devnet accounts; check-in points do not pay ORE; bandwidth/cloud-hosting providers and funded rewards are not connected. See [recording runbook](README.md).
