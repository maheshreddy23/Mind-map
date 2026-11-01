# Mind-map – UI Design Spec (v1)

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
Bottom bar on Home, Library, Ask, Inbox: **Home · Library · [Talk] · Ask · Inbox**.
- Talk is a 62 dp accent circle raised 28 dp above the bar, opens Session.
- Inbox shows a count badge when entries are waiting.
- Topic, Session and Butler are pushed screens with a back arrow (no bottom bar).
- Butler opens from the "M" avatar on Home.

## 4. Screens

### Home
1. Greeting ("Good evening, {name}") + headline "What shall we shelve today?" + M avatar (→ Butler).
2. **Nudge card** (amber-soft): "M · Today's nudge", one-sentence nudge from the nudge engine, buttons **Talk now** (ink) and **Later**.
3. **Inbox strip** when count > 0: "{n} entries waiting for your call" → Inbox.
4. **Continue**: last active topic card (subject, title, entries, open questions) → Topic.
5. **Your shelves**: subject tiles; empty subjects say "Empty".

### Session (focus mode)
- Top: back, **Listen / Interview** segmented control, **Wrap up** pill.
- Mode hint under it ("M stays quiet and just captures." / "M asks one sharp follow-up at a time.").
- Transcript: user turns with caption "You · {audio}s · transcribed in {t}s"; M's questions in Newsreader 20 with a 2 dp accent rule on the left and the label "M asks".
- Live partial transcript in muted colour while recording.
- Bottom: live waveform bars, 76 dp mic button (accent idle, record-red with halo while recording), hint text.
- Show model loading status here ("Loading speech model…") per v1 behaviour.

### Wrap-up and Inbox (same screen; after Wrap up it opens with the session's results)
- Headline: "I filed {n} entries. {m} need your call."
- **Needs-you cards** first: amber border, chips (Needs you, type, privacy), the entry text, M's suggestion ("Project Management › Trade-offs (new topic)"), buttons **File it there / Change / Discard**. On accept, collapse to a confirmation line.
- **Filed automatically** list: chip, text, breadcrumb link to the topic, **Undo**.

### Library
- Title + totals ("{topics} topics · {entries} entries").
- Search field (label "Search everything you've said") using FTS5.
- Privacy filter chips: All / Private / Personal / Shareable.
- Per subject: heading + topic **book spines** (rounded 6/14/14/6, 6 dp darker inset on the spine edge, title in Newsreader, entry count). Spine colours rotate from a fixed palette (accent, amber-text, violet, slate) with ≥ 4.5:1 white text.
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
- User question bubble (ink, right-aligned).
- M's answer: avatar + "From {n} of your entries", answer in Newsreader 19 with numbered citation chips that open the cited entry.
- No match: "Nothing yet, I'm afraid. Shall I interview you on it now?" + **Start an interview**.
- Bottom input: text field + voice button; bottom bar below.

### Butler
- Avatar, "Your butler", one-line personality summary.
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
- Talk is reachable in one tap from Home, Library, Ask and Inbox.
