# Mind-map – Product Requirements v2

**Owner:** Mahesh **Status:** Draft for review, not yet sent to Claude Code **Platform:** Android, OnePlus 13R (Snapdragon 8 Gen 3, 12 GB+ RAM), fully on-device

---

## 1. Vision

A private library of everything I know, think and have learned, built by talking. A personal butler with its own name and personality listens, organises what I say into subjects and topics, checks with me when unsure, nudges me to capture and revisit, and answers when I ask the library something.

## 2. Principles

1. **Fully local.** No cloud, no accounts, no telemetry. All speech, AI and storage stay on the phone.
2. **Ask when unsure.** Organise automatically when confident; confirm with me when not. Never silently misfile.
3. **Shareable later, private by default.** Every entry carries a privacy level from day one, so sharing later never leaks personal material.
4. **Calm and clean.** Typography-first, minimal chrome, no themed decoration.
5. **My data stays portable.** Everything can be exported as plain Markdown.

## 3. Users and phases

- **Now:** me only, on my phone.
- **Soon:** friends install the app on their own phones. Every install is fully independent: its own butler, its own library, no accounts, no server, and no data flowing between phones.
- **What that requires:** self-serve onboarding (a device check, a recommended model tier, an in-app model download); support for weaker phones (6–8 GB RAM should get smaller models automatically); distribution as a signed APK through GitHub Releases.
- **Sharing between people:** export Shareable topics as a **library pack** file that a friend can import into their own library, read-only and credited to the author. Private and Personal entries never leave the phone.

## 4. The butler

### 4.1 Roles

One companion, three modes:

| Mode | Behaviour |
| --- | --- |
| **Listen** | I talk freely about anything; the butler stays quiet and just captures. |
| **Interview** | The butler asks one short follow-up at a time to draw out reasons, examples, mistakes and lessons. The interview style is configurable, for example the curious fresher (the earlier Arjun persona), a peer, or a sceptical reviewer. |
| **Librarian** | Files, merges, links, summarises, nudges and answers questions about the library. |

### 4.2 Personality creation (onboarding)

A first-run wizard sets:

- **Name** (changeable any time; the default is an original name, not borrowed from any film or comic character)
- **How it addresses me** (for example "Mahesh" or "sir")
- **Tone sliders:** formal ↔ casual, reserved ↔ warm, serious ↔ witty, brief ↔ detailed
- **Voice:** Android TTS or a Piper voice, plus speech rate
- **Values and quirks:** a short free-text description of its character

### 4.3 Keeping its own personality

- The personality is stored as an editable **persona profile** that is always included in the model's instructions, so it stays consistent across sessions and model swaps.
- A **butler's journal** keeps a short running memory: what it has learned about how I like things filed, recurring themes, and promises it made ("I'll remind you about ECO handling"). This is summarised and fed back into the prompt.
- **Honest limit:** the model's weights do not change. "Growing" means a richer persona profile, journal and catalogue, not self-training.
- Renaming or editing the personality takes effect immediately; history is kept.

## 5. Capture

- Push-to-talk and continuous listening (VAD), plus typed input.
- Talk about **anything**: work, life, ideas, opinions.
- Each session is transcribed live; transcripts are saved as soon as they are ready.
- An editable **domain vocabulary** list (PLM, CCPM, RFP, Teamcenter, NX, CATIA, jig, fixture, datum, BOM, ECO, and so on) is passed to Whisper as its initial prompt.
- **Wrap up** triggers filing (section 6).

## 6. Organisation

### 6.1 Structure

**Subject → Topic → Entry**

- **Subject:** a broad area, for example Jigs and Fixtures, PLM, Project Management, Personal Finance, Life.
- **Topic:** a book within a subject, for example "RFP to project kickoff".
- **Entry:** one captured piece, from one session or part of a session.

One session can produce several entries across different topics.

### 6.2 Entry types

Each entry is tagged with one type:

- **Knowledge:** facts, procedures, how things work
- **Lesson:** what went wrong or right, and why
- **Principle:** my rules of thumb and judgement calls (this builds the "My principles" book, the personality layer)
- **Idea:** future possibilities
- **Reflection:** personal thoughts
- **Open question:** things still unresolved

### 6.3 Automatic filing with confirmation

- At wrap-up, the butler splits the session into entries and proposes subject, topic, type and privacy level for each.
- **High confidence:** filed automatically, shown in a "Filed today" summary that can be undone.
- **Low confidence:** sent to an **Inbox** for me to confirm, rename, move or reject.
- New subjects and topics are always confirmed before creation.
- Never use a catch-all "General" bucket.

### 6.4 Maintenance

- Each topic page is a coherent, rewritten summary of its entries (renumbered, deduplicated), with every entry still viewable on its own.
- Move, merge, split, rename and delete subjects, topics and entries; pages regenerate after changes.
- Links between related topics, suggested by the butler.
- Test and junk sessions can be deleted completely.

## 7. Ask the library

- Ask questions by voice or text, for example "What have I said about datum selection?"
- Uses on-device semantic search (a small open-source embedding model) together with full-text search (SQLite FTS5).
- Answers **cite the entries** they came from, with tap-through to each one.
- If the library has nothing on the subject, the butler says so plainly and offers to start an interview on it.

## 8. Privacy and sharing

- **Privacy level per entry:** Private (never leaves the phone), Personal, or Shareable. Proposed at filing and editable later.
- **Sensitivity guard:** warns when a session appears to contain employer-confidential details such as programme names, part numbers, drawing numbers, customer names or schedules. Suggests rewording into a general lesson, or marking the entry Private.
- **App lock:** biometric or PIN.
- **Encrypted database** using SQLCipher or equivalent open-source encryption.
- **Sharing (later phase):** export selected topics, Shareable entries only, as Markdown or PDF, or as a read-only library file.
- **Backup:** encrypted export to a local file that I choose where to keep.

## 9. Nudges

| Nudge | Example |
| --- | --- |
| Daily capture prompt | "Anything worth keeping from today?" |
| Gap alert | "You've covered PLM releases five times but never ECO handling." |
| Resurface (spaced review) | "Three months ago you said X. Still true?" |
| Unfinished topic | "The fixture-tolerancing book has two open questions." |
| Inbox reminder | "Four entries are waiting for your confirmation." |
| Weekly digest | A summary of what was added and what's growing. |

- Settings for frequency, quiet hours and per-type on/off.
- Notifications with quick actions: Talk now, Later, Dismiss.
- Heavy AI work for nudges is precomputed while the phone is charging (Android WorkManager), so nudges don't drain the battery.

## 10. Models

- A **model manager** shows installed models, their size, verification status and which is active, for speech-to-text, the LLM, embeddings and voice.
- **Recommended tiers** for 12 GB RAM:
  - Speech-to-text: Whisper small.en (default) or base.en (faster)
  - LLM: a 3–4B model by default; up to about 7–8B at Q4 as a quality option, slower
  - Embeddings: a small sentence-embedding model (around 100 MB)
- Every model is checked against its official checksum.
- **Download decision (open):** either (a) keep the current approach of downloading in the browser and picking the file, which preserves a release build with no INTERNET permission, or (b) add an in-app downloader, which needs network permission. **Recommendation: (a).**

## 11. Design

**Direction:** clean, calm, editorial. Typography-first, generous spacing, light and dark modes, Material 3 foundations without decoration.

**References:**

- Mobbin.com for real app screen patterns
- Readwise Reader for calm reading pages
- Obsidian for linked notes and graph ideas
- Things 3 for clean hierarchy and the inbox pattern
- Apple Journal for gentle prompts and nudges
- Bear for typography in notes

**Screens:**

1. **Home:** butler greeting, today's nudge card, Continue last topic, a large Talk button.
2. **Session:** live transcript, mic, mode switch (Listen, Interview), Wrap up.
3. **Inbox:** entries awaiting confirmation, with one-tap accept or change.
4. **Library:** subjects grid → topic list → topic page → entry.
5. **Ask:** question box, answers with citations.
6. **Butler:** name, personality, journal, rename and edit.
7. **Settings:** models, voice, vocabulary, nudges, privacy, backup.

## 12. Non-functional requirements

- Fully offline at runtime.
- Transcription at 0.5× real time or faster with small.en (currently achieved: 0.48×).
- First LLM token under 2 seconds when warm.
- Models preload when a session opens, with visible loading status.
- No data loss: every turn is saved immediately.
- Battery-aware: heavy jobs only while charging; respects battery saver.
- Updates install over the previous version (fixed signing key, already in place).

## 13. Delivery phases

1. **Data model and filing:** subjects, topics, entries, types, privacy levels, inbox, delete and regenerate, and removal of "General".
2. **Butler persona:** onboarding wizard, rename, persona profile, butler's journal, modes.
3. **Ask the library:** embeddings, semantic search, answers with citations.
4. **Nudges:** scheduled jobs, notifications, quick actions.
5. **Privacy:** sensitivity guard, app lock, encryption, backup.
6. **Design pass:** the new clean UI across all screens.
7. **Sharing:** export of Shareable topics (later).

Each phase must pass CI and be tested on the phone before the next one starts.

## 14. Open decisions for Mahesh

1. Model downloading: browser and file picker (recommended), or an in-app downloader? - in app downloader i think.shall give both options
2. Default butler name and the starting personality. - name shall be 'M' personality like Tony stark's Jarvis&#32;
3. Should Interview mode default to the curious-fresher style? Give intelligence of a Mtech graduate from Top IITs&#32;
4. Default privacy level for new entries: Personal or Private? Private
5. Nudge frequency to start with: daily or every other day? Daily&#32;

### Decisions (1 Nov 2026)

1. **Models: both options.** An in-app downloader (from Hugging Face, checksum-verified, shows the size first, Wi-Fi only by default) plus file import. Network access is used only for model downloads. Also build a **Sealed** flavour with no INTERNET permission, for anyone who wants a guaranteed offline build.
2. **Default butler: "M".** Default personality: calm, dry-witted, unflappable, anticipatory, formal but warm, and efficient. It's an original character inspired by that archetype, not named after or quoting any film character. Every user creates their own butler during onboarding and can rename it any time.
3. **Interview mode default: a sharp engineer** with M.Tech-level fundamentals who is new to the company and its practices. It asks probing why, how and what-if questions and challenges vague answers, but still asks for the explanations a newcomer would need. The curious-fresher style stays as an option. Note: the persona sets the style; the size of the model sets the real depth.
4. **Default privacy: Private.**
5. **Nudges: daily,** with quiet hours.
