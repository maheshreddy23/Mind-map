package com.freshernotes.core.library

import com.freshernotes.core.Slug
import com.freshernotes.core.notes.NoteParser
import com.freshernotes.core.notes.Section

/** One typed piece cut from a v1 session note. */
data class LegacyPiece(val type: EntryType, val title: String, val body: String)

/**
 * Splits a v1 per-session note (Key concepts / Procedures / Reasons / Pitfalls / Open questions) into v2 typed
 * entries: the first three become one Knowledge entry, pitfalls a Lesson and open questions an Open question.
 * Nothing is dropped: if no section can be parsed, the whole note becomes one Knowledge entry.
 */
object LegacyNotes {
    private const val EMPTY = "_(nothing recorded)_"

    fun split(notesMarkdown: String, title: String): List<LegacyPiece> {
        if (notesMarkdown.isBlank()) return emptyList()
        val note = NoteParser.parse(notesMarkdown)
        fun items(s: Section) = note.sections[s].orEmpty().filterNot { it.contains(EMPTY) }

        val knowledge = buildString {
            if (note.subtopics.isNotEmpty()) append("**Sub-topics:** ").append(note.subtopics.joinToString(", ")).append("\n\n")
            for (s in listOf(Section.CONCEPTS, Section.PROCEDURES, Section.RATIONALE)) {
                val list = items(s)
                if (list.isEmpty()) continue
                append("**").append(s.heading).append("**\n")
                list.forEach { append(it).append('\n') }
                append('\n')
            }
        }.trim()
        val pieces = buildList {
            if (knowledge.isNotEmpty()) add(LegacyPiece(EntryType.KNOWLEDGE, title, knowledge))
            items(Section.PITFALLS).takeIf { it.isNotEmpty() }?.let { add(LegacyPiece(EntryType.LESSON, "$title: pitfalls", it.joinToString("\n"))) }
            items(Section.OPEN_QUESTIONS).takeIf { it.isNotEmpty() }?.let { add(LegacyPiece(EntryType.OPEN_QUESTION, "$title: open questions", it.joinToString("\n"))) }
        }
        val onlySubtopics = pieces.size == 1 && pieces[0].body.startsWith("**Sub-topics:**") && !pieces[0].body.contains("\n\n")
        return if (pieces.isEmpty() || onlySubtopics) listOf(LegacyPiece(EntryType.KNOWLEDGE, title, notesMarkdown.trim())) else pieces
    }
}

/**
 * Keyword guess of a subject for v1 topics, which had none. Only used by the one-off v1 → v2 migration, where
 * there is no LLM. It is deliberately strict: a guess is "confident" only with clear evidence, and only subjects
 * named in requirements-v2 §6.1 may be created without asking. Everything else goes to the Inbox.
 */
object SubjectClassifier {
    data class Seed(val title: String, val keywords: Set<String>, val preApproved: Boolean)
    data class Guess(val subject: String, val score: Int, val confident: Boolean)

    val seeds = listOf(
        Seed(
            "Jigs and Fixtures",
            setOf("jig", "jigs", "fixture", "fixtures", "locator", "locators", "clamp", "clamps", "clamping", "datum", "datums",
                "bush", "bushes", "drill", "tooling", "pin", "pins", "tolerance", "tolerancing", "assembly jig", "gauge", "template"),
            preApproved = true,
        ),
        Seed(
            "PLM",
            setOf("plm", "teamcenter", "bom", "eco", "ecn", "ecr", "change order", "release", "workflow", "revision",
                "item", "dataset", "baseline", "configuration", "effectivity", "vault", "check in", "check out"),
            preApproved = true,
        ),
        Seed(
            "Project Management",
            setOf("rfp", "ccpm", "kickoff", "kick off", "schedule", "milestone", "buffer", "critical chain", "gantt",
                "stakeholder", "budget", "proposal", "deliverable", "resource", "scope", "risk register"),
            preApproved = true,
        ),
        Seed("Personal Finance", setOf("salary", "investment", "invest", "mutual fund", "tax", "savings", "loan", "emi", "stock", "sip"), preApproved = true),
        Seed("Life", setOf("family", "health", "habit", "habits", "friend", "friends", "travel", "sleep", "exercise"), preApproved = true),
        // Plausible but not named in the spec: proposed in the Inbox, never created silently.
        Seed("CAD", setOf("catia", "nx", "sketch", "part design", "surface", "modelling", "modeling", "drawing", "cad", "feature tree", "constraint"), preApproved = false),
    )

    const val MIN_SCORE = 3

    fun guess(title: String, text: String): Guess? {
        val haystack = " " + Slug.of("$title $title $text").replace('-', ' ') + " "
        val scored = seeds.map { seed -> seed to seed.keywords.sumOf { kw -> occurrences(haystack, " ${Slug.of(kw).replace('-', ' ')} ") } }
            .sortedByDescending { it.second }
        val (best, score) = scored.firstOrNull() ?: return null
        if (score == 0) return null
        val runnerUp = scored.getOrNull(1)?.second ?: 0
        val confident = best.preApproved && score >= MIN_SCORE && score >= 2 * runnerUp
        return Guess(best.title, score, confident)
    }

    private fun occurrences(haystack: String, needle: String): Int {
        var count = 0
        var i = haystack.indexOf(needle)
        while (i >= 0) {
            count++
            i = haystack.indexOf(needle, i + needle.length - 1)
        }
        return count
    }
}

/** v1 rows as read from the version-2 database. */
data class LegacyTopic(val id: Long, val title: String, val markdown: String)
data class LegacySession(val id: Long, val title: String, val startedAt: Long, val topicId: Long?, val notesMarkdown: String)

data class PlannedEntry(
    val sessionId: Long?,
    val type: EntryType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val status: EntryStatus,
    /** Filed entries: the topic. Inbox entries: the proposed existing topic, if any. */
    val topicId: Long?,
    val proposedSubject: String?,
    val confidence: Double,
    val reason: String,
)

data class MigrationPlan(
    /** Subjects to create (spec-named only), in order. */
    val subjects: List<String>,
    /** v1 topic id -> subject title, for topics confidently classified. */
    val topicSubjects: Map<Long, String>,
    val entries: List<PlannedEntry>,
)

/**
 * Plans the one-off v1 → v2 migration without touching the database (so it is unit-testable):
 * - every v1 session note is split into typed entries ([LegacyNotes]);
 * - a v1 topic whose subject is confidently guessed is put under that subject and its entries are filed
 *   (marked auto-filed so they show in "Filed today" with Undo);
 * - everything else goes to the Inbox, pre-filled with the v1 topic and the best subject guess;
 * - a v1 topic page that no session note fed (hand-made) is split into entries too, so nothing is lost.
 * Sessions and turns are never removed.
 */
object LegacyPlanner {
    fun plan(topics: List<LegacyTopic>, sessions: List<LegacySession>): MigrationPlan {
        val byTopic = sessions.filter { it.notesMarkdown.isNotBlank() }.groupBy { s -> s.topicId?.takeIf { id -> topics.any { it.id == id } } }
        val subjects = LinkedHashSet<String>()
        val topicSubjects = HashMap<Long, String>()
        val entries = ArrayList<PlannedEntry>()

        for (t in topics) {
            val notes = byTopic[t.id].orEmpty()
            val guess = SubjectClassifier.guess(t.title, t.markdown + "\n" + notes.joinToString("\n") { it.notesMarkdown })
            val confident = guess?.confident == true
            if (confident) {
                subjects += guess!!.subject
                topicSubjects[t.id] = guess.subject
            }
            val status = if (confident) EntryStatus.FILED else EntryStatus.INBOX
            val reason = when {
                confident -> ""
                guess == null -> "From v1: subject unclear"
                else -> "From v1: subject unclear (best guess ${guess.subject})"
            }
            val pieces = if (notes.isNotEmpty()) {
                notes.sortedBy { it.startedAt }.flatMap { s -> LegacyNotes.split(s.notesMarkdown, s.title).map { s to it } }
            } else {
                LegacyNotes.split(t.markdown, t.title).map { null to it }
            }
            for ((s, p) in pieces) {
                entries += PlannedEntry(
                    sessionId = s?.id, type = p.type, title = p.title, body = p.body, createdAt = s?.startedAt ?: 0L,
                    status = status, topicId = t.id, proposedSubject = guess?.subject,
                    confidence = if (confident) 0.8 else 0.4, reason = reason,
                )
            }
        }
        // Session notes whose topic is missing: Inbox, with only a subject guess.
        for (s in byTopic[null].orEmpty()) {
            val guess = SubjectClassifier.guess(s.title, s.notesMarkdown)
            for (p in LegacyNotes.split(s.notesMarkdown, s.title)) {
                entries += PlannedEntry(
                    sessionId = s.id, type = p.type, title = p.title, body = p.body, createdAt = s.startedAt,
                    status = EntryStatus.INBOX, topicId = null, proposedSubject = guess?.subject,
                    confidence = 0.3, reason = "From v1: no topic",
                )
            }
        }
        return MigrationPlan(subjects.toList(), topicSubjects, entries)
    }
}
