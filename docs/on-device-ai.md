# On-device AI (LiteRT-LM, after Google AI Edge Gallery)

Branch: `feature/ai-edge-gallery` (merged into `integration/0.2.9-candidate`, not into `main`). Status: **built and JVM-tested, not run on a device.** No on-device
generation has been observed. Treat every on-device answer path as NOT_RUN until a phone session records one.

## What was taken from Google AI Edge Gallery

[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) (Apache-2.0, Copyright 2025 Google LLC)
is a Kotlin/Compose Android app that runs LLMs on the phone. Inspected at commit
`a8e795660ee5c02b983897553c7844c351c77bda` (2026-10-09). Relevant parts:

| Gallery | What it does | EdgeORE |
|---|---|---|
| `model_allowlists/<version>.json` | Each model is a Hugging Face repo + file + `commitHash` + `sizeInBytes` + sampler defaults | Same shape, two entries copied from `1_0_20.json` into `app/src/main/assets/ai/ondevice-model-allowlist.json`; EdgeORE adds the Hugging Face SHA-256 of the pinned file, `license` and `gated` |
| `ModelAllowlist.kt` download URL | `https://huggingface.co/<repo>/resolve/<commitHash>/<file>?download=true` | Same URL, always the pinned commit |
| `GlobalModelManager` / WorkManager download, HF OAuth for gated models | Background download, sign-in for gated repos | Foreground download in the ViewModel scope; **no Hugging Face sign-in**, so gated models are listed but not downloadable |
| `LlmChatModelHelper.kt` | LiteRT-LM `Engine` → `Conversation.sendMessage`, GPU or CPU | Same calls, CPU only, single-turn, in `ondevice-llm/.../LiteRtLmBridge.java` |
| Runtime `com.google.ai.edge.litertlm:litertlm-android:0.18.0` | | **0.8.0** (see below) |

No gallery source file is copied. Attribution is in [`NOTICE`](../NOTICE).

## Why LiteRT-LM 0.8.0 and a Java bridge module

Solana Mobile and the Kotlin toolchain are pinned for qualification (Kotlin 2.0.21, coroutines 1.9.0).
LiteRT-LM POMs:

| Version | Declares |
|---|---|
| 0.8.0 | gson 2.13.2, kotlinx-coroutines-android 1.9.0 |
| 0.9.0 – 0.16.1 | + kotlin-reflect 2.2.21 (would lift kotlin-stdlib to 2.2.21) |
| 0.17.0 – 0.18.0 | gson 2.14.0, kotlin-reflect 2.4.0, coroutines 1.11.0 |

0.8.0 is the only release that does not move a pinned version. Its classes carry Kotlin 2.2 metadata, which the
Kotlin 2.0.21 compiler rejects ("binary version of its metadata is 2.2.0, expected 2.0.0"). So LiteRT-LM sits
behind `:ondevice-llm`, a Java-only Android library: LiteRT-LM is its `implementation` dependency and never
reaches the app's Kotlin compile classpath. 0.8.0 references `kotlin.reflect.full` without declaring it, so the
module adds `kotlin-reflect:2.0.21` (runtime only, the project's own Kotlin version). gson's
`error_prone_annotations:2.41.0` is excluded so the unit-test classpath keeps `2.28.0`.

Resolved dependency diff against `main` (`2442ba1`), app module:

- `debugRuntimeClasspath`: **added** `litertlm-android:0.8.0`, `gson:2.13.2`, `kotlin-reflect:2.0.21`. Nothing changed version.
- `debugCompileClasspath`: unchanged (only `project :ondevice-llm`).
- `debugUnitTestRuntimeClasspath`: same three additions. Nothing changed version.
- `debugAndroidTestRuntimeClasspath`: unchanged.

Raw `gradle dependencies` output is in [`docs/evidence-ai-edge-gallery/`](evidence-ai-edge-gallery/).

## Model allowlist

| id | File | Size | License | Download |
|---|---|---|---|---|
| `qwen2.5-1.5b-instruct-q8` | `litert-community/Qwen2.5-1.5B-Instruct` @ `19edb84c69a0` / `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm` | 1,597,931,520 bytes (1.60 GB) | Apache-2.0 | Yes; SHA-256 `faa60663…2dc9` |
| `gemma3-1b-it-int4` | `litert-community/Gemma3-1B-IT` @ `42d538a932e8` / `gemma3-1b-it-int4.litertlm` | 584,417,280 bytes (584 MB) | Gemma Terms of Use | **No**: gated on Hugging Face; this build has no sign-in |

The Qwen file's commit (2025-11-25) predates the 0.8.0 AAR build (2025-12-02), so the format should be one 0.8.0
reads. That is an inference from dates, **not a tested fact**.

The allowlist parser fails closed: a non-commit pin, a path in the file name, a non-`.litertlm` file, a missing
SHA-256 on an ungated entry, a non-positive size or a duplicate id rejects the whole list.

## Flow in the app (Private AI screen, "On-device model" card (LiteRT-LM))

1. Choose a model chip. Each shows size and `not downloaded` / `downloaded` / `gated`.
2. **Download … ** opens a consent dialog: size in GB and exact bytes, source repo, commit, license, free space,
   a metered-network warning and a RAM warning when the phone reports less than the gallery's minimum.
   Nothing is fetched before **Download** is tapped.
3. The file streams to `filesDir/models/<id>/<file>.part` over HTTPS (redirects must stay on HTTPS). It is
   renamed into place only if the byte count and SHA-256 match the allowlist; otherwise the partial file is
   deleted. Cancel deletes it too.
4. With a downloaded model, the prompt box enables. The first prompt loads the model (LiteRT-LM, CPU), then
   generates in-process. The status line says whether a network was up: an answer is labelled offline only if
   no network was active before and after the run (`InferenceClaim.classify`, unchanged).
5. A `LOCAL_AI` receipt records prompt/response/model digests with outcome `COMPLETED_ON_DEVICE_OFFLINE` or
   `COMPLETED_IN_APP_NETWORK_UP`. Contents are not stored.
6. **Delete from phone** unloads the model and removes its directory.

The owned-host (Ollama) path is unchanged. Prompts on the on-device path are never sent over the network; the
only network use is the weights download the user confirmed.

## Not done / not verified

- **No device run.** Engine load, generation speed, memory use, cancel behaviour and model-format compatibility
  with 0.8.0 are all unobserved. The JVM tests use a fake runtime; the native library is never loaded in tests.
- CPU backend only. GPU needs `uses-native-library` OpenCL declarations and per-device testing.
- Download runs in the ViewModel scope: it stops if Android kills the process. No resume (a restart re-downloads),
  no WorkManager.
- No Hugging Face sign-in, so Gemma (gated) cannot be downloaded in-app.
- Single-turn prompts only; no streaming, no multimodal, no document attachment on this path.
- APK size: debug APK 15,080,965 bytes on `main` (gate evidence for `d675002bd701`; `app/` is unchanged since) → 56,742,870 bytes on this branch (+41.7 MB). Almost all of it is `liblitertlm_jni.so`, stored uncompressed for arm64-v8a (18.7 MB) and x86_64 (21.8 MB). There is no armeabi-v7a/x86 build of LiteRT-LM; on those phones loading fails and the app reports "Not run". Restricting ABIs or compressing native libs would shrink it; neither was done.
- Build gate for `f4651f83532f`: [`evidence/build-f4651f83532f/summary.md`](../evidence/build-f4651f83532f/summary.md) — 175 tests, 174 passed, 1 skipped (the live node-agent test), lint clean.
- Screenshot baselines (`docs/screenshots/`) were not re-recorded; the Private AI screen changed.
- Nothing here is a production-readiness claim, and AI use earns nothing (no ORE, no income).
