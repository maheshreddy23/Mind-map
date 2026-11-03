# MYM – Map your Mind

A private, on-device library of what you know, built by talking. A butler you name and shape (default **M**)
listens, asks one sharp question at a time, files what you said into **Subject → Topic → Entry**, checks with you
when unsure, nudges you to capture and revisit, and answers questions from your own entries with citations.

Everything runs on the phone. The network is used only to download models you choose (Full build); the
**Sealed** build has no `INTERNET` permission at all and takes model files by import.

| Function | Component | Licence |
|---|---|---|
| Speech-to-text | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (submodule) | MIT |
| Voice activity detection | Silero VAD via whisper.cpp; energy VAD fallback | MIT |
| LLM and embeddings | [llama.cpp](https://github.com/ggml-org/llama.cpp) (submodule) | MIT |
| Text-to-speech | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) + Piper voice; Android TTS fallback | Apache-2.0 |
| Search | SQLite FTS5 + on-device sentence embeddings | Apache-2.0 / public domain |
| App | Kotlin, Jetpack Compose, Room, Hilt, WorkManager, coroutines | Apache-2.0 |

Target device: OnePlus 13R (Snapdragon 8 Gen 3, 12 GB RAM, Android 15). Minimum SDK 29, arm64-v8a only.
Application id `com.maheshreddy.mym`.

## 1. Models

Models are not in the APK or in git.

**Full build (recommended):** on first launch the onboarding checks the phone (RAM, chip) and recommends a set
that fits, with the total size. Tap Download: files come from Hugging Face over HTTPS at a pinned revision, Wi-Fi
only unless you allow mobile data, with progress, pause and resume, and each file is checked against its official
SHA-256 before it is used (a mismatch is deleted). **Settings → Models** has the full catalogue (speech, language
models, embeddings, voices) with size, RAM, expected speed on your phone, quality tier, licence and a
"Recommended for your phone" mark; you can switch, delete (never the only active one) and see storage used.

Recommended on a 12 GB Snapdragon 8 Gen 3: Whisper small.en, Qwen3-4B-Instruct-2507 (Q4_K_M), BGE small en v1.5
(embeddings, 37 MB), a Piper en_GB voice and Silero VAD. The exact files and hashes are in
`core/src/main/kotlin/com/maheshreddy/mym/core/models/CatalogData.kt`.

**Sealed build, or offline:** download the same files on a computer from the Hugging Face links shown in the
catalogue and import them in **Settings → Models → Import** (Storage Access Framework). The app copies them into
private storage.

**Model licences.** The app is MIT/Apache-2.0, but weights carry their own terms; the catalogue shows each one.
Qwen2.5-3B (Qwen Research), Llama 3.2, Gemma 3 and EmbeddingGemma are not open-source licences: they are listed
with a label and never recommended. Piper voices use espeak-ng data (GPL-3.0).

## 2. Install

Download the APKs from the latest green **Android CI** run (Actions → run → Artifacts → `mym-apks`): `app-full-*.apk` (downloads models) or `app-sealed-*.apk` (no internet, import only). Or build it:

```bash
git clone --recursive https://github.com/maheshreddy23/Mind-map.git
```

```bash
cd Mind-map && ./gradlew assembleFullRelease assembleSealedRelease
```

The release APKs are in `app/build/outputs/apk/{full,sealed}/release/`. It is signed with the debug key,
for personal sideloading. Install it with:

```bash
adb install -r app/build/outputs/apk/full/release/app-full-release.apk
```

Build requirements: JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1. The sherpa-onnx prebuilt
libraries are downloaded once at build time (`:tts:fetchSherpaOnnx`).

**Updates:** CI signs every APK with one fixed key held in the `DEBUG_KEYSTORE_B64` repository secret,
so a new APK installs over the old one and keeps your models and library. (MYM has a new application id, so it installs next to the old Fresher Notes app rather than over it.) Without that secret each CI run
uses a random key (CI shows a warning) and updating needs an uninstall. To set it up once:

```bash
keytool -genkeypair -keystore debug.keystore -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=MYM Debug"
```

```bash
base64 -w0 debug.keystore | gh secret set DEBUG_KEYSTORE_B64 --repo maheshreddy23/Mind-map
```

Keep `debug.keystore` somewhere safe; it is never committed (`*.keystore` is git-ignored).

## 3. Use

1. **Onboarding** (first run): device check → recommended models and total size → download → name your butler,
   choose its interview style and the areas you want to map → microphone → first session.
2. **Talk** (centre button): speak, or type. In **Interview** mode the butler asks one probing question at a time;
   in **Listen** mode it just captures. Tap the check (Wrap up, 5 s to undo) or say "wrap up".
3. The filing sheet opens in the session: entries it was sure of are filed (with Undo); the rest need your call
   (File / Change / Discard). Uncertain ones wait in the **Inbox** (icon with a badge on Home).
4. **Library**: shelves per subject with progress, gaps worth filling, FTS5 search, privacy filter.
5. **Ask**: questions answered only from your entries, found by meaning and keywords, with numbered citations
   that open the entry; optional spoken answers; privacy filter. Nothing relevant → "Start an interview on this".
6. **Map**: subjects as clusters, topics sized by entries, suggested links (dashed) you can confirm, gaps ringed
   in amber; pinch-zoom, filters, Growth animation.
7. **Settings** (avatar menu on Home): models, speech recognition (language, domain vocabulary, mic test), voice,
   butler, nudges (types, time, quiet hours), privacy (default Private, app lock), storage, encrypted backup,
   performance benchmark, about and licences.

## How it works

- Every turn is written to Room the moment it exists, so a crash loses nothing. A session that ended
  while notes were still being written resumes note generation on the next launch.
- The LLM prompt is the persona plus a rolling window of the conversation. When the prompt passes 70% of
  the context window (4096 tokens), older turns are summarized into a running summary.
- llama.cpp keeps the KV cache between turns and only decodes the new part of the prompt, which keeps
  time-to-first-token low.
- The mic is muted while the butler speaks, so it does not transcribe itself.
- Every entry is embedded in the background (incrementally after each filing; large backlogs only while charging) for Ask and the Map.
- Under thermal pressure or battery saver the app uses fewer CPU threads and switches to push-to-talk.
  At critical temperature the LLM pauses; transcription and saving continue.

## Development

```text
core/     pure Kotlin: VAD endpointing, prompts, filing, retrieval, map layout, catalogue, nudges (JVM tests)
whisper/  whisper.cpp JNI + Silero VAD
llama/    llama.cpp JNI (streaming, prefix KV cache)
tts/      sherpa-onnx Piper + Android TTS fallback
app/      Compose UI, Room, Hilt, conversation loop, filing, search, embeddings, Map, model manager
```

```bash
./gradlew assembleFullDebug assembleSealedDebug testFullDebugUnitTest :core:test
```

Instrumented tests need the stand-in models (whisper tiny.en, Silero, Qwen2.5-0.5B, a Piper voice) on a
device or emulator:

```bash
scripts/download-test-models.sh && scripts/push-test-models.sh
```

```bash
./gradlew connectedDebugAndroidTest
```

(For an x86_64 emulator add `-PwithX86_64`.) See [PROGRESS.md](PROGRESS.md) for status and known limitations.
