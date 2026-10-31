Goal
A fully offline Android app for a OnePlus 13R (Snapdragon 8 Gen 3, 12 GB+ RAM, Android 15). The user talks out loud about what he has learned over 3–4 years as an aerospace engineer (jigs and fixtures, PLM, CATIA, NX, Teamcenter, and so on). The AI plays a curious fresher who just joined the company: it listens, asks doubts and follow-up questions out loud, and when the session ends it writes detailed, organized notes into a topic-wise knowledge base.
Hard constraints
• No network calls at runtime. Remove the INTERNET permission from the release build.
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
