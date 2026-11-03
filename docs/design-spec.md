# MYM – Map your Mind – UI Design Spec (v2)

Source of truth for the design pass. Reference mockups: the "Mind-map App Redesign" canvas
(https://claude.ai/artifact/4xd5ukCtPg5SKVZRCv3LXK). Translate to Jetpack Compose + Material 3;
do NOT embed the HTML. Where this file and the canvas disagree, this file wins.

## 1. Principles
1. One tap to talk from every top-level screen (centre button in the bottom bar).
2. Session is a dark **focus mode**: only the transcript, M's question and the mic.
3. Nothing is filed silently: confident entries are filed with **Undo**; doubtful ones wait in the **Inbox**.
4. Every answer in Ask **cites** the entries it came from. If nothing is shelved, say so and offer an interview.
5. Privacy is always visible: a chip (Private / Personal / Shareable) on every entry.
6. Calm and editorial: typography first, no gradients, no emoji, no decoration.

## 2. Design tokens

### Colour (light theme)
| Token | Hex | Use |
|---|---|---|
| ground | #F7F5F0 | App background |
| surface | #FFFFFF | Cards, bottom bar |
| ink | #1C1B19 | Primary text, primary dark buttons |
| muted | #6B6760 | Secondary text |
| line | #E4E0D8 | Borders, dividers |
| field-line | #D6D1C7 | Input borders |
| accent | #1F5F5B | Brand teal: Talk button, links, active nav, primary buttons |
| accent-soft | #E6EFEE | Knowledge chip, "why it matters" box |
| amber-text | #8A4712 | Nudge label, open-question text |
| amber-soft | #F3E7D7 | Nudge card |
| amber-tint | #FBEBDD | Open-question chip and box |
| amber-border | #D9A45C | "Needs you" card border |
| violet-soft / text | #ECE7F5 / #4B3A78 | Principle chip |
| neutral-chip / text | #ECEAE5 / #4A4640 | Privacy chip |
| record | #B3261E | Mic while recording |

### Colour (Session focus mode, always dark)
| Token | Hex |
|---|---|
| bg | #14171A |
| text | #ECE9E3 |
| muted | #A3A09A |
| control-bg | #22272B |
| line | #3A4146 |
| accent-on-dark | #7FC4BC |
| question rule | #2F5F5A |

Provide a full dark theme for the other screens too (derive from these: ground #14171A, surface #1C2024, ink #ECE9E3, accent #7FC4BC). All text must meet 4.5:1 contrast.

### Type
- Display / reading: **Newsreader** (OFL, bundle the font files; no network).
- UI: **Inter** (OFL, bundled).
- Scale: screen title 30–32 Newsreader 500 · topic card title 18–21 Newsreader · M's question 20 Newsreader · body 15–17 Inter · labels 13 Inter 600 · captions 11–12 Inter.

### Shape and spacing
- Screen padding 20 dp; vertical gap between blocks 14–18 dp.
- Card radius 14–18 dp, 1 dp `line` border, no shadows (only the Talk button has a soft shadow).
- Pill buttons: height 44–48 dp, radius = height / 2.
- Chips: 11 sp 600, padding 4×8, radius 10.
- Touch targets ≥ 44 dp everywhere.

## 3. Navigation
Bottom bar on Home, Library, Ask and Map: **Home · Library · [Talk] · Ask · Map**.
- Talk is a 62 dp accent circle raised 28 dp above the bar, opens Session.
- The **Inbox** is not a tab: it is an icon button at the top right of Home with a count badge (no badge at zero) and opens as a full screen with a back arrow.
- The butler's avatar (its initial) at the top right of Home opens a small menu: **{name}, your butler** (→ Butler) and **Settings**. Butler and Settings have no tab.
- Topic, Session, Inbox, Butler, Settings and Models are pushed screens with a back arrow (no bottom bar).
- First run shows **Onboarding** instead of Home, once.

## 4. Screens

### Home
1. Greeting ("Good evening, {name}") + headline "What shall we shelve today?" + Inbox icon (badge) + butler avatar (menu: Butler, Settings).
2. **Nudge card** (amber-soft): "{butler} · Today's nudge", one-sentence nudge from the nudge engine, buttons **Talk now** (ink) and **Later**.
3. **Needs-your-call card** when N > 0: "{N} entries need your call" + **Review** → Inbox.
4. **Continue**: last active topic card (subject, title, entries, open questions) → Topic.
5. **Your shelves**: subject tiles; empty subjects say "Empty".
6. **Recent sessions**: topic (or session title) + one-line summary of what was filed + status.

### Session (focus mode)
- Top: back, **Listen / Interview** segmented control, overflow menu (**Rename**, **Delete session**). No Wrap up pill.
- Mode hint under it ("M stays quiet and just captures." / "M asks one sharp follow-up at a time.").
- Transcript: user turns with caption "You · {audio}s · transcribed in {t}s"; M's questions in Newsreader 20 with a 2 dp accent rule on the left and the label "M asks".
- Live partial transcript in muted colour while recording.
- Bottom: live waveform bars, 76 dp mic button (accent idle, record-red with halo while recording), hint text.
- Show model loading status here ("Loading speech model…") per v1 behaviour.
- Input row: text field + a context-sensitive button: **Send** while the field has text; when empty it is **Wrap up** (accent circle with a check, accessibility label "Wrap up"). Tapping Wrap up shows "Wrapping up… **Undo**" for 5 s before filing starts. Saying "wrap up" still works.
- After filing, the results open as a **bottom sheet** inside the session (see Wrap-up). Closing the sheet returns to Home.
- Reopening a session left open: a card offers **Resume** or **Wrap up**.

### Wrap-up sheet (in Session) and Inbox (full screen from Home)
- Headline: "I filed {n} entries. {m} need your call."
- **Needs-you cards** first: amber border, chips (Needs you, type, privacy), the entry text, the butler's suggestion ("Project Management › Trade-offs (new topic)"), buttons **File / Change / Discard**. On accept, collapse to a confirmation line.
- **Filed automatically** list: chip, text, breadcrumb link to the topic, **Undo**. The sheet ends with **Done** (→ Home).
- Inbox also lists sessions left open over 12 h (**Wrap up** / **Open it**).
- Inbox empty state: "Nothing needs your call", three stat tiles (entries this week, topics, day streak), then **Last filed** (5 entries, each with Undo).

### Library
- Title + totals ("{topics} topics · {entries} entries").
- Search field (label "Search everything you've said") using FTS5.
- Privacy filter chips: All / Private / Personal / Shareable.
- Per subject: heading + topic **book spines** (rounded 6/14/14/6, 6 dp darker inset on the spine edge, title in Newsreader, entry count). Spine colours rotate from a fixed palette (accent, amber-text, violet, slate) with ≥ 4.5:1 white text.
- Each shelf heading shows "{n} topics · {m} entries" and a thin progress bar: the share of topics that are solid (more than one entry, nothing open), "{p}% solid".
- **Gaps worth filling** card (up to 3): domain words from the user's vocabulary that no entry mentions ("No entries on ECO yet"), topics with open questions, thin topics; each with **Talk**.
- Empty subject: dashed card "Empty shelf. {suggestion}?" + **Talk**.
- "+ New subject" button.

### Topic page
- Back + overflow menu (rename, merge, move, delete, rewrite summary).
- Subject, title (Newsreader 30), "From {n} sessions · rewritten by M {date}".
- Tabs **Summary | Entries · {n}**.
  - Summary: numbered steps, "Why it matters" box (accent-soft), "Open question" box (amber-tint).
  - Entries: cards with type + privacy chips, text, source session date.
- Sticky bottom: **Ask about this** (outline) and **Continue topic** (accent, mic icon → Session pre-scoped to this topic).

### Ask
- Filter row: privacy chips (Private / Personal / Shareable; entries outside the filter are never given to the model) + **Speak answers** toggle.
- Empty state: 3–4 suggested questions built from the user's topics (tap to ask). Empty library: an explanation + **Start a session**.
- User question bubble (ink, right-aligned).
- Butler's answer: avatar + "From {n} of your entries", answer in Newsreader 19 with numbered citation chips that open the cited entry. Retrieval is by meaning (on-device embeddings) and keywords (FTS5).
- No match: "Nothing in your entries covers that yet. Shall I interview you on it now?" + **Start an interview on this**.
- Bottom input: text field + voice button; bottom bar below.

### Map
- Title, stats ("{n} topics · {l} links · {g} gaps"), privacy chips, time chips (**All / This month / This week**) and a **Growth** toggle that plays topics in by the date they were added.
- Canvas (pinch to zoom, drag to pan): subjects are coloured clusters (spine palette, tint fill + outline, name above); topics are circles sized by entry count with the count inside and the title below; links between related topics (shared terms or close embeddings): **dashed = suggested by the butler**, solid once confirmed.
- Gaps: thin topics (≤ 1 entry) or topics with open questions get a dashed amber ring; empty subjects have a dashed amber outline.
- Tap a topic: preview card (subject, title, summary, entries, open questions) with **Open topic** and **Continue by talking** (gap: **Interview me on this**). Tap an empty subject: **Interview me on this**. Tap a link: **Confirm link / Not related** (confirmed: **Remove link**).
- Empty library: the starting areas chosen in onboarding are drawn as empty clusters labelled **Start here**.

### Settings
- Sections: Models, Speech recognition, Voice, Butler, Nudges, Privacy, Storage, Backup, Performance, About. Models opens the model manager (catalogue per category, device fit, download with progress, pause/resume, SHA-256 check, import, activate, delete).

### Onboarding (first run, once)
- Progress bar of 7 steps: **Welcome → Your phone (device check) → Recommended for your phone (bundle, sizes, total, mobile-data switch) → Downloading (per-model progress) → Your butler (name, what it calls you, interview style, areas to map) → Microphone (+ notifications) → Ready (Start first session / Later)**.
- An existing install sees it once, with current choices pre-filled. The Sealed build skips downloads (Import files).

### Butler
- Avatar, the butler's actual name as the heading (never "Your butler"), one-line personality summary; the interview-style description wraps in full.
- Fields: **Name**, **Calls you**.
- Personality sliders: Formal↔Casual, Reserved↔Warm, Serious↔Witty, Brief↔Detailed.
- Interview style chips: **Sharp engineer** (default) / Curious fresher / Sceptical reviewer, with a one-line description of the selected one.
- **M's journal** card (latest learned preferences and promises).
- **Save personality** (ink, full width).

## 5. Motion
- Mic: halo pulse while recording; waveform bars driven by real input level.
- Screen transitions: Material shared-axis; Session enters with a fade to dark.
- Filing results animate in one by one (≤ 200 ms each).
- Respect the system "remove animations" setting.

## 6. Done when
- All screens above exist in Compose with light + dark themes, using the tokens as a Compose theme (no hard-coded colours in screens).
- Fonts bundled; app stays fully offline.
- Compose screenshot tests for each screen in both themes.
- Talk is reachable in one tap from Home, Library, Ask and Map.
