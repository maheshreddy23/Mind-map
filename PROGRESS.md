# Progress

| Phase | Scope | Status |
|---|---|---|
| 1 | Project skeleton, Compose UI shell, Room database, CI workflow | Done locally, CI not yet run (push blocked) |
| 2 | whisper.cpp JNI, WAV transcription instrumented test | Done locally, CI not yet run (push blocked) |
| 3 | Live microphone + VAD feeding whisper | Done locally, awaiting CI |
| 4 | llama.cpp JNI, streaming tokens, persona prompt | Not started |
| 5 | TTS (sherpa-onnx + Piper, Android TTS fallback) | Not started |
| 6 | Conversation loop + context summarization | Not started |
| 7 | Note generation, topic merge, FTS5 search, Markdown export | Not started |
| 8 | Polish: battery/thermal, errors, README, release without INTERNET | Not started |

## Phase 1: skeleton
Built:
- Gradle (Kotlin DSL + version catalog), AGP 8.7, Kotlin 2.0, Compose (Material 3), Hilt, Room via KSP.
- `:core`: a pure-Kotlin JVM module for logic that does not need Android (fast unit tests).
- `:app`: Home, Session, Notes and Settings screens, Room (`topics`, `sessions`, `turns`), DataStore settings.
- The release manifest strips `INTERNET`/`ACCESS_NETWORK_STATE` with `tools:node="remove"`.
- `.github/workflows/android.yml`: build + unit tests job, plus an emulator job (API 34, x86_64) for instrumented tests.

Tests:
- `:core` `SlugTest`, `:app` `AppSettingsTest` (JVM).
- `DatabaseTest` (instrumented): turns persist in order; topics are de-duplicated by slug.

## Phase 2: whisper.cpp
Built:
- `third_party/whisper.cpp` submodule pinned to v1.9.5.
- `:whisper` Android library: `whisper_jni.cpp` (load, transcribe with an optional domain prompt, abort, free), plus `WhisperEngine` (a coroutine and mutex wrapper).
- Static CPU-only ggml with hidden symbols, so it cannot clash with llama.cpp's own ggml copy. On arm64 it builds with `armv8.2-a+dotprod+fp16+i8mm`.
- `:core` `Wav`: decodes RIFF WAV (16-bit or float, any channel count), averages to mono, resamples to 16 kHz and encodes.
- `scripts/download-test-models.sh` (cached in CI) and `scripts/push-test-models.sh` (adb push to `/data/local/tmp/models`).

Tests:
- `WavTest` (JVM): round trip, resampling, decoding `jfk.wav`. Passes locally.
- `WhisperEngineTest` (instrumented): transcribes `jfk.wav` with `ggml-tiny.en` and checks the transcript.
- Verified locally: `whisper_jni.cpp` compiles and links against whisper.cpp for host x86_64. The Android build itself has not been verified locally (no SDK or NDK here).

## Phase 3: live microphone + VAD
Built:
- `AudioRecorder`: `AudioRecord` with the `VOICE_RECOGNITION` source, 16 kHz mono, emitted as a Flow of float chunks.
- `:core` `Endpointer`: an endpointing state machine with start/end hysteresis, a 300 ms pre-roll, a 900 ms end-of-speech silence and a 28 s cap per utterance (whisper's window is 30 s).
- `EnergySpeechDetector` (adaptive noise floor) as the fallback; `SileroVad` (whisper.cpp's streaming Silero VAD) when the optional `ggml-silero` model is installed.
- `SpeechInput`: push-to-talk (tap to start, tap to stop) or continuous mode, transcribed on one ordered worker, with a domain-vocabulary prompt for whisper. It can be muted while text-to-speech is talking.
- `TranscriptFilter`: drops `[BLANK_AUDIO]` and known whisper hallucinations ("Thank you.").
- `ModelStore` and the Settings model section: pick each model with the Storage Access Framework (a file, or a folder for the Piper voice), copied into `filesDir/models/<kind>` with progress shown.

Tests:
- `EndpointerTest` (6) and `TranscriptFilterTest` (4) on the JVM. They pass locally and caught a real bug: after a forced cut at the 28 s cap, the noise floor reset to the speaker's own voice level and the rest of the speech was lost.
- `StreamingPipelineTest` (instrumented): `jfk.wav` padded with silence is fed frame by frame through the Silero and the energy VAD into whisper.

# Assumptions and decisions
- **Push and CI:** GitHub refused this session's push (HTTP 403, Claude GitHub App not authorized), and Google Maven and the SDK downloads (`dl.google.com`) are blocked here. Phases are therefore committed locally and checked only as far as this container allows: `:core` JVM tests and host builds of the native code. The Android build and the emulator tests run only in CI, which has not run yet.
- **Test models on the emulator:** instrumented tests read models from `/data/local/tmp/models` by streaming them through `UiAutomation.executeShellCommand("cat ...")`, because apps cannot open that directory directly.
- **One ggml copy per engine:** whisper.cpp and llama.cpp each live in their own Gradle module and `.so` file, each with its own static ggml. This avoids CMake target name clashes and symbol interposition between the two.
- **Release signing:** release builds are signed with the debug key so the APK installs for personal use. There is no Play Store distribution.
- **VAD:** Silero (via whisper.cpp) is optional because it is a separate model file; the energy VAD covers the case where it is missing.
- **Push-to-talk is tap-to-toggle,** not press-and-hold, because holding the button through a long explanation is tiring.
