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

## Phase 4: llama.cpp
Built:
- `third_party/llama.cpp` submodule pinned to b12021, built statically and CPU-only (no OpenSSL or networking).
- `llama_jni.cpp`: load, streaming generation through a `TokenCallback` with UTF-8-safe chunking, abort, token counting, the model's chat template, and runtime thread count.
- Prefix KV cache: the tokens already in the cache are remembered, and each call decodes only the part of the prompt that changed (the persona and earlier turns are reused). This is what keeps time-to-first-token low as a conversation grows.
- `LlamaEngine`: a cancellable `Flow<String>` of chunks; cancelling the flow aborts native generation.
- `:core` `PromptFormat` (ChatML, Gemma, Llama 3, Phi, chosen from the GGUF's template); `Persona` (the spec's system prompt, "wrap up" detection, reply cleanup that keeps one question and caps length).

Tests:
- `PromptTest` (JVM, 6).
- `LlamaEngineTest` (instrumented, Qwen2.5-0.5B): ChatML detected; the persona returns a streamed question of 40 words or fewer; cancelling a stream leaves the engine usable; prefix-cached follow-up timing is logged.

## Phase 5: text-to-speech
Built:
- `:tts` module. The prebuilt sherpa-onnx v1.13.8 Android libraries are downloaded once at build time by `fetchSherpaOnnx` and are never fetched at runtime. Its `Tts.kt` binding is vendored unchanged from the same tag (Apache-2.0).
- `PiperSynthesizer`: a VITS/Piper voice folder (`.onnx`, `tokens.txt`, `espeak-ng-data/`), synthesized sentence by sentence so speech starts early, played through `AudioTrack`.
- `SystemSynthesizer`: the Android `TextToSpeech` fallback. `Engines` falls back to it automatically if no Piper voice is installed or the voice fails to load.

Tests:
- `PiperSynthesizerTest` (instrumented): synthesizes a question with `vits-piper-en_US-amy-low` and checks the sample rate, duration and that the output is not silent; also tests sentence splitting.

## Phase 6: conversation loop and context management
Built:
- `ConversationController`: each senior turn is saved immediately → `QuestionPolicy` (every N turns, or "wrap up") → `ContextManager` builds the prompt → if it uses more than 70% of the context, the older turns are summarized into `sessions.runningSummary` → streamed question → saved → spoken with the mic muted.
- Staying in character: the latest senior turn carries a short "ask ONE question" reminder. If the reply still is not a question, the controller retries once at temperature 0.2 with a stricter reminder, then falls back to a generic follow-up ("Could you give me a concrete example of that?"). CI found this with the 0.5B model, which summarized the senior's explanation in a numbered list instead of asking.
- `ContextManager` keeps at least the 6 most recent turns verbatim and folds at least half of the unsummarized turns each time, so summarization does not run again on every turn. It merges consecutive same-role turns for chat templates.
- Session screen: streamed "Arjun is thinking…" text, speaking and summarizing states, and error messages (for example a missing model, with a pointer to Settings).

Tests:
- `ContextManagerTest` (JVM, 6) with a fake model: role mapping, merging, the 70% threshold, which turns get folded, the previous summary carried forward, and the question cadence.
- `ConversationLoopTest` (instrumented, real Qwen2.5-0.5B, captured TTS): a senior turn produces a saved and spoken question of 40 words or fewer; "let's wrap up" ends the session without a new question.

## Phase 7: notes, topics, search, export
Built:
- `NotePrompts` / `NoteParser`: the spec's sections (topic and sub-topics, key concepts, procedures, rationale, pitfalls, open questions). The parser tolerates heading variants, `**bold**` headings, numbering and "none".
- `TopicMatcher` (word-set Jaccard similarity with light stemming and stop words) merges "Drill jig" into "Drill Jigs and Fixtures". `TopicPageMerger` adds only new bullets, keeps sections the user added by hand, unions sub-topics, keeps a session log, and is idempotent.
- `NoteGenerator`: the session is marked PENDING when it ends, so generation resumes on next launch if the app is killed. It uses the full transcript, or the running summary plus later turns when the transcript does not fit.
- `SearchIndex`: SQLite **FTS5** (porter + unicode61, bm25 with title weighted ×3, snippets) over topic pages and session transcripts. Android's built-in SQLite does not include FTS5, so this uses `androidx.sqlite:sqlite-bundled`.
- `MarkdownExporter`: writes `Documents/FresherNotes/<slug>.md` through MediaStore (no storage permission needed); it runs automatically after each merge and from the Notes screen.
- Room v2 with a hand-written `MIGRATION_1_2` (session notes status and Markdown, topic link).

Tests:
- `NotesTest` (JVM, 5).
- `NotesPipelineTest` (instrumented): FTS5 prefix search, ranking, re-indexing and injection-safe queries; the full wrap-up → notes → merge into an existing similar topic → search → export round trip.

## Phase 8: polish
Built:
- `DeviceMonitor` + `PerformancePolicy`: thermal status listener, battery saver and battery level. Moderate heat → 2 fewer LLM threads; severe → half the threads and push-to-talk only; critical → the LLM pauses (transcription and saving continue). Low battery while not charging → throttled, push-to-talk only.
- `PerfStats`: live speech-to-text real-time factor and LLM first-token time and tokens per second, shown in Settings, plus a **Benchmark the LLM** button for checking the < 2 s target on the device.
- The screen stays on during a live session; native models are released on `onTrimMemory` when memory is critical.
- Error states: missing model (banner on Home, message in Session), notes failed (retried on launch), microphone and permission errors, and a "too hot" pause.
- README covers model downloads (with licence notes), install and usage.
- CI builds the release APK and fails if `aapt2 dump permissions` shows `INTERNET`.

Tests:
- `PerformancePolicyTest` (JVM, 4).

# Known limitations
- **Performance targets are not measured by CI.** GitHub's x86_64 emulator says nothing about a Snapdragon 8 Gen 3. Use Settings → Benchmark the LLM and the live speech-to-text real-time factor on the phone. [Likely] base.en runs well faster than real time and Qwen2.5-3B Q4_K_M reaches its first token in under 2 s for a typical turn with a warm cache. The first turn of a session (cold cache plus model load) can exceed 2 s.
- **Model licences:** the spec's Qwen2.5-3B (Qwen Research License) and Gemma (Gemma Terms) are not MIT/Apache. Phi-4-mini (MIT) and Qwen2.5-1.5B (Apache-2.0) are supported, permissively licensed alternatives.
- **Android TTS fallback:** this is only offline if an offline voice is installed on the phone.
- **Silero VAD is weaker in this port:** whisper.cpp's Silero implementation does not use the model's inter-frame audio context, so it scores speech conservatively (about 20% of `jfk.wav` frames are at or above 0.5). Endpointing still works because it needs only 250 ms of consecutive speech to start, but quiet speakers may want the energy VAD (remove the VAD model in Settings).
- **No GPU or NPU acceleration:** whisper, llama and sherpa run on the CPU only. The Adreno GPU (Vulkan/OpenCL) and the Hexagon NPU are unused.
- **Notes quality depends on the LLM:** a 3B model can drop details from long sessions, because it sees the running summary rather than the full transcript.
- **Small-model output in CI:** the 0.5B stand-in model is only asserted to produce a question-shaped reply, not a good one.

# Assumptions and decisions
- **Push and CI:** GitHub refused this session's push (HTTP 403, Claude GitHub App not authorized), and Google Maven and the SDK downloads (`dl.google.com`) are blocked here. Phases are therefore committed locally and checked only as far as this container allows: `:core` JVM tests and host builds of the native code. The Android build and the emulator tests run only in CI, which has not run yet.
- **Test models on the emulator:** instrumented tests read models from `/data/local/tmp/models` by streaming them through `UiAutomation.executeShellCommand("cat ...")`, because apps cannot open that directory directly.
- **One ggml copy per engine:** whisper.cpp and llama.cpp each live in their own Gradle module and `.so` file, each with its own static ggml. This avoids CMake target name clashes and symbol interposition between the two.
- **Release signing:** release builds are signed with the debug key so the APK installs for personal use. There is no Play Store distribution.
- **VAD:** Silero (via whisper.cpp) is optional because it is a separate model file; the energy VAD covers the case where it is missing.
- **Push-to-talk is tap-to-toggle,** not press-and-hold, because holding the button through a long explanation is tiring.
- **Phases 4–7 were developed together** while push access was blocked and the first CI runs were pending. They were pushed after Phases 1–3 went green, so they share one CI run rather than one each.
- **Topic matching** uses word-set similarity, not embeddings, so it is deterministic and offline with no extra model. Synonyms ("PLM" vs "product lifecycle management") are not merged.
- **Notes generation** runs in an application-scoped coroutine, not WorkManager. It is resumed on launch from the PENDING/FAILED status in Room.
- **Push-to-talk ignores taps shorter than 0.3 s.** Whisper also gets a domain prompt (CATIA, NX, Teamcenter, …) to improve jargon recognition.
