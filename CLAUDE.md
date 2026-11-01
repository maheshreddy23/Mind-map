Product spec
docs/requirements-v2.md is the current product spec (Mind-map v2: a personal butler that captures, files and answers from a Subject → Topic → Entry library). Where v2 and this file disagree, v2 wins, except for the technical constraints below, which still apply unless the "v2 decisions" section changes them. The original v1 brief (fresher notes) is kept below for history; v1 is built and shipped (see PROGRESS.md).

v2 decisions (requirements-v2 §14, decided 1 Nov 2026)
1. Models: both options. An in-app downloader (Hugging Face, checksum-verified, shows the size first, Wi-Fi only by default) plus file import (Storage Access Framework). Network is used only for model downloads. A "Sealed" product flavour is built with no INTERNET permission (import only); CI must verify the Sealed release APK has no INTERNET permission. This replaces the v1 rule "remove INTERNET from the release build".
2. Default butler name "M". Default personality: calm, dry-witted, unflappable, anticipatory, formal but warm, efficient. An original character inspired by that archetype: never named after, or quoting, any film or comic character. Every user creates their own butler in onboarding and can rename it any time.
3. Interview mode default: a sharp engineer with M.Tech-level fundamentals who is new to the company and its practices. Asks probing why / how / what-if questions, challenges vague answers, still asks for the explanations a newcomer needs. The curious-fresher (Arjun) style stays as an option. The persona sets the style; the model size sets the real depth.
4. Default privacy level for new entries: Private.
5. Nudges: daily, with quiet hours.

v2 delivery rules
• Phases follow requirements-v2 §13, one PR per phase, starting from main. Each phase passes CI and is tested on the phone before the next starts. Exception (owner's decision, 1 Nov 2026): the design pass (phase 6) runs right after phase 1, for existing screens only; Ask and Butler are built in that design when their phases land.
• UI: docs/design-spec.md is the source of truth. Colours come only from the Mind theme tokens (ui/theme/Theme.kt); fonts are bundled (Newsreader, Inter). Every screen has a stateless …Content composable with light and dark Roborazzi screenshots in app/src/test/screenshots; re-record with ./gradlew recordRoborazziDebug when a change to a screen is intended.
• Updates must install over the existing app with data intact: fixed signing key, Room migrations only (never fallbackToDestructiveMigration), and an instrumented migration test for every schema version bump. Room schemas are exported to app/schemas and committed.
• Never delete user sessions or turns during a migration. v1 sessions are migrated into Subject → Topic → Entry; anything the migration is unsure about goes to the Inbox.
• No catch-all "General" subject or topic, anywhere (prompts, parsers, fallbacks).
• The in-app model downloader and the Full/Sealed flavours land with onboarding (phase 2), because self-serve onboarding is what needs them.
• Keep the persona and journal as a stable prompt prefix so llama.cpp's prefix KV cache reuses it: the v1 phone baseline is 1848 ms warm first token at 12.4 tok/s, only ~150 ms under the 2 s target.

v1 brief (history)
Goal
A fully offline Android app for a OnePlus 13R (Snapdragon 8 Gen 3, 12 GB+ RAM, Android 15). The user talks out loud about what he has learned over 3–4 years as an aerospace engineer (jigs and fixtures, PLM, CATIA, NX, Teamcenter, and so on). The AI plays a curious fresher who just joined the company: it listens, asks doubts and follow-up questions out loud, and when the session ends it writes detailed, organized notes into a topic-wise knowledge base.
Hard constraints
• No network calls at runtime, except user-initiated model downloads in the Full flavour (v2 decision 1). The Sealed flavour has no INTERNET permission.
• Only open-source components with permissive licences (MIT or Apache 2.0). No paid APIs or subscriptions.
• Language: Kotlin with Jetpack Compose. Native code in C++ via the NDK and CMake.
• Minimum SDK 29, target SDK 35, arm64-v8a only.
Tech stack
Function
Component
Speech-to-text
whisper.cpp (git submodule), model ggml-small.en or ggml-base.en, plus a VAD
LLM
llama.cpp (git submodule), model Qwen2.5-3B-Instruct Q4_K_M GGUF (Gemma 3 4B as an option)
Text-to-speech
sherpa-onnx with a Piper English voice; Android TextToSpeech as a fallback
Storage
Room (SQLite) plus Markdown export to Documents/FresherNotes
Architecture
MVVM, Kotlin coroutines, Hilt
Model handling
• Models are not committed to git. On first launch, the app lets the user pick model files from local storage (Storage Access Framework) and copies them into app-private storage.
• Document the expected file names and download links in README.md.
• Tests use tiny stand-in models (whisper tiny.en, a small GGUF such as Qwen2.5-0.5B) that CI downloads and caches.
Fresher persona (system prompt)
You are Arjun, a fresher engineer who just joined the user's aerospace team. You are eager and humble. Listen to what the senior explains. Ask ONE short, specific question at a time: clarify jargon, ask for reasons ("why is it done this way?"), ask for examples, mistakes to avoid, and how things connect. Never lecture. Keep replies under 40 words. When the senior says "wrap up", stop asking questions.
Conversation loop
1. Push-to-talk button, with an option for continuous mode using the VAD.
2. Audio (16 kHz mono) goes to whisper, which returns a transcript.
3. The transcript and a rolling conversation window go to the LLM, which returns a question.
4. The question is spoken aloud through TTS.
5. Repeat. Every turn is saved to the database immediately, so nothing is lost if the app crashes.
6. Context management: when the context window passes 70%, summarize the older turns into a running summary.
Note generation (at session end)
The LLM turns the session into Markdown notes with these sections:
• Topic and sub-topics
• Key concepts explained
• Procedures or steps
• Reasons and rationale
• Pitfalls and lessons learned
• Open questions to revisit
Notes are tagged by topic and merged into existing topic pages where a match exists. The app includes full-text search (SQLite FTS5).
Screens
1. Home: list of topics and recent sessions, plus a Start session button.
2. Session: live transcript, a mic button, the fresher's questions, and a Wrap up button.
3. Notes: Markdown viewer per topic, with edit and export.
4. Settings: model picker, voice choice, response length, and a setting for how many questions the fresher asks.
Build phases (each must pass CI before moving on)
1. Project skeleton, Compose UI shell, Room database, CI workflow.
2. whisper.cpp JNI integration; transcribe a WAV file in an instrumented test.
3. Live microphone recording with VAD feeding whisper.
4. llama.cpp JNI integration, streaming tokens, persona prompt; test that it returns a question.
5. TTS with sherpa-onnx and Piper, plus the fallback.
6. Full conversation loop and context summarization.
7. Note generation, topic merge, search, Markdown export.
8. Polish: battery and thermal handling, error states, README, and a release build without the INTERNET permission.
CI workflow requirements (.github/workflows/android.yml)
• Trigger on push and pull request.
• Set up JDK 17, the Android SDK, NDK, and CMake; check out submodules recursively.
• Cache Gradle and the test models.
• Run ./gradlew assembleDebug testDebugUnitTest.
• Run instrumented tests with reactivecircus/android-emulator-runner (API 34, x86_64; also build an x86_64 ABI for emulator tests only).
• Upload app-debug.apk as an artifact.
Definition of done
• All phases are merged and CI is green.
• PROGRESS.md lists what was built, test results, and known limitations.
• README.md covers model download, install, and usage.
• Performance targets on a Snapdragon 8 Gen 3: speech-to-text faster than real time, and the first LLM token in under 2 seconds.
