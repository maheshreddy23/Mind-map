# Fresher Notes

An offline Android app that turns what you know into organized notes. You explain your work out loud
(jigs and fixtures, PLM, CATIA, NX, Teamcenter…). **Arjun**, an AI "fresher" who just joined your team,
listens and asks one short follow-up question at a time. When you say **"wrap up"**, he writes topic-wise
Markdown notes and merges them into your knowledge base.

Everything runs on the phone. The release build has no `INTERNET` permission.

| Function | Component | Licence |
|---|---|---|
| Speech-to-text | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) v1.9.5 (submodule) | MIT |
| Voice activity detection | Silero VAD via whisper.cpp; energy VAD fallback | MIT |
| LLM | [llama.cpp](https://github.com/ggml-org/llama.cpp) b12021 (submodule) | MIT |
| Text-to-speech | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) v1.13.8 + Piper voice; Android TTS fallback | Apache-2.0 |
| Search | SQLite FTS5 (bundled SQLite from `androidx.sqlite`) | Apache-2.0 / public domain |
| App | Kotlin, Jetpack Compose, Room, Hilt, coroutines | Apache-2.0 |

Target device: OnePlus 13R (Snapdragon 8 Gen 3, 12 GB RAM, Android 15). Minimum SDK 29, arm64-v8a only.

## 1. Download the models (on a computer or the phone)

Models are not in the APK or in git. Download them once, put them anywhere on the phone (e.g. `Download/`),
then pick them in **Settings → Models**. The app copies them into its private storage.

| Slot | Recommended file | Size | Download |
|---|---|---|---|
| Speech-to-text * | `ggml-base.en.bin` (fast) or `ggml-small.en.bin` (more accurate) | 148 MB / 488 MB | https://huggingface.co/ggerganov/whisper.cpp/tree/main |
| Voice activity detection | `ggml-silero-v5.1.2.bin` | 0.9 MB | https://huggingface.co/ggml-org/whisper-vad/tree/main |
| Language model * | `qwen2.5-3b-instruct-q4_k_m.gguf` | 2.1 GB | https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/tree/main |
| (alternative LLM) | `gemma-3-4b-it-Q4_K_M.gguf` | 2.5 GB | https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/tree/main |
| (permissive LLM) | `Phi-4-mini-instruct-Q4_K_M.gguf` (MIT) or `qwen2.5-1.5b-instruct-q4_k_m.gguf` (Apache-2.0) | 2.5 GB / 1.1 GB | https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/tree/main |
| Piper voice (folder) | `vits-piper-en_US-amy-low` (or any `vits-piper-en_*`) | ~65 MB | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models |

\* required.

**Model licences.** The app's code and libraries are MIT/Apache-2.0, but model weights carry their own
terms. The spec's default, Qwen2.5-**3B**-Instruct, is under the *Qwen Research License*, and Gemma under
the *Gemma Terms of Use*. Neither is MIT/Apache, so check them before any non-personal use. For a fully
permissive stack use Phi-4-mini-instruct (MIT) or Qwen2.5-1.5B-Instruct (Apache-2.0); the app detects
their prompt formats automatically.

Without a Silero model the app uses an energy-based VAD. Without a Piper voice it uses
Android's built-in TextToSpeech (install an offline English voice in Android settings to keep it offline).

The Piper voice is a **folder**: extract the `.tar.bz2` (for example with a file manager or on a computer)
and pick the extracted `vits-piper-en_US-amy-low` folder. It must contain the `.onnx` model, `tokens.txt`
and `espeak-ng-data/`.

The prompt format is detected from the GGUF's chat template (ChatML for Qwen, Gemma's own format for
Gemma, Llama 3 format for Llama).

## 2. Install

Download `app-debug.apk` from the latest green **Android CI** run (Actions → run → Artifacts), or build it:

```bash
git clone --recursive https://github.com/maheshreddy23/Mind-map.git
```

```bash
cd Mind-map && ./gradlew assembleRelease
```

The release APK is at `app/build/outputs/apk/release/app-release.apk`. It is signed with the debug key,
for personal sideloading. Install it with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Build requirements: JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1. The sherpa-onnx prebuilt
libraries are downloaded once at build time (`:tts:fetchSherpaOnnx`).

**Updates:** CI signs every APK with one fixed key held in the `DEBUG_KEYSTORE_B64` repository secret,
so a new APK installs over the old one and keeps your models and notes. Without that secret each CI run
uses a random key (CI shows a warning) and updating needs an uninstall. To set it up once:

```bash
keytool -genkeypair -keystore debug.keystore -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fresher Notes Debug"
```

```bash
base64 -w0 debug.keystore | gh secret set DEBUG_KEYSTORE_B64 --repo maheshreddy23/Mind-map
```

Keep `debug.keystore` somewhere safe; it is never committed (`*.keystore` is git-ignored).

## 3. Use

1. **Settings → Models**: pick the whisper model and the GGUF (plus the optional VAD and Piper voice).
2. **Home → Start session.** Tap the mic, explain something, tap again. Arjun transcribes it, thinks,
   and asks a question out loud. Or turn on **Continuous mode** in Settings and just talk: the VAD
   detects when you pause. You can also type.
3. Say **"let's wrap up"** (or tap **Wrap up**). Arjun stops asking and writes notes with these sections:
   topic and sub-topics, key concepts, procedures or steps, reasons and rationale, pitfalls and lessons
   learned, open questions to revisit.
4. Notes are merged into the matching topic page (similar titles such as "Drill jig" and "Drill Jigs and
   Fixtures" are treated as one topic). Each page is also written to `Documents/FresherNotes/<topic>.md`.
5. **Home → search** finds topics and past sessions (FTS5, prefix matching). **Notes** pages can be edited
   and re-exported.

Settings also control voice, speech rate, maximum reply length, how often Arjun asks (every 1–5 of your
turns), and include a **Benchmark the LLM** button plus live speech-to-text and LLM timings.

## How it works

- Every turn is written to Room the moment it exists, so a crash loses nothing. A session that ended
  while notes were still being written resumes note generation on the next launch.
- The LLM prompt is the persona plus a rolling window of the conversation. When the prompt passes 70% of
  the context window (4096 tokens), older turns are summarized into a running summary.
- llama.cpp keeps the KV cache between turns and only decodes the new part of the prompt, which keeps
  time-to-first-token low.
- The mic is muted while Arjun speaks, so he does not transcribe himself.
- Under thermal pressure or battery saver the app uses fewer CPU threads and switches to push-to-talk.
  At critical temperature the LLM pauses; transcription and saving continue.

## Development

```text
core/     pure Kotlin: VAD endpointing, WAV, prompts, context management, notes parsing/merging (JVM tests)
whisper/  whisper.cpp JNI + Silero VAD
llama/    llama.cpp JNI (streaming, prefix KV cache)
tts/      sherpa-onnx Piper + Android TTS fallback
app/      Compose UI, Room, Hilt, conversation loop, notes, search, export
```

```bash
./gradlew assembleDebug testDebugUnitTest :core:test
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
