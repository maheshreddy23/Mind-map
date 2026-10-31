# Progress

| Phase | Scope | Status |
|---|---|---|
| 1 | Project skeleton, Compose UI shell, Room database, CI workflow | In CI |
| 2 | whisper.cpp JNI, WAV transcription instrumented test | Not started |
| 3 | Live microphone + VAD feeding whisper | Not started |
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
