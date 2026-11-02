package com.freshernotes.core.library

import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Slug
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.ContextManager
import com.freshernotes.core.llm.Role
import com.freshernotes.core.notes.TopicMatcher

/** The library as the filer sees it: confirmed subjects and their topics. */
data class TopicRef(val id: Long, val title: String)
data class SubjectRef(val id: Long, val title: String, val topics: List<TopicRef>)

/** One entry the model proposed at wrap-up, before any decision is made. */
data class EntryProposal(
    val subject: String?,
    val topic: String?,
    val type: EntryType?,
    val title: String,
    val body: String,
    val modelSaysHigh: Boolean,
    val privacy: Privacy?,
)

/** What to do with one proposal. Ids are set only for subjects/topics that already exist. */
data class FilingDecision(
    val status: EntryStatus,
    val subjectId: Long?,
    val topicId: Long?,
    val proposedSubject: String?,
    val proposedTopic: String?,
    val type: EntryType,
    val privacy: Privacy,
    val title: String,
    val body: String,
    /** 0..1; >= [FilingPolicy.AUTO_FILE] means filed automatically. */
    val confidence: Double,
    /** Why it went to the Inbox (shown to the user); empty when filed. */
    val reason: String,
)

object FilingPrompts {
    const val MAX_LISTED_TOPICS = 80

    fun system(): String =
        "You are a careful librarian. You split a spoken session into separate library entries and file each one. " +
            "Use only what the speaker actually said; keep names, numbers and reasons exactly. One entry per distinct idea. " +
            "Each entry gets a SUBJECT (a broad area, for example PLM or Jigs and Fixtures), a TOPIC (a specific book inside " +
            "that subject, 2-6 words) and a TYPE. Reuse an existing subject and topic from the library list when one fits; " +
            "otherwise propose a new, specific name. Never use a catch-all name such as General, Misc or Other. " +
            "TYPE is one of: Knowledge (facts, procedures, how things work), Lesson (what went wrong or right, and why), " +
            "Principle (rules of thumb, judgement calls), Idea (future possibilities), Reflection (personal thoughts), " +
            "Open question (unresolved). CONFIDENCE is high only if the subject and topic clearly fit; otherwise low. " +
            "PRIVACY is Private for anything personal or employer-specific, Personal for personal but harmless, " +
            "Shareable only for general knowledge. Write every entry in exactly this format:\n" +
            "=== ENTRY\nSUBJECT: <subject>\nTOPIC: <topic>\nTYPE: <type>\nCONFIDENCE: <high or low>\nPRIVACY: <privacy>\n" +
            "TITLE: <short title>\n- <bullet>\n- <bullet>"

    fun library(subjects: List<SubjectRef>): String {
        if (subjects.isEmpty()) return "The library is empty. Propose new subjects and topics."
        var budget = MAX_LISTED_TOPICS
        return buildString {
            append("Library (subject: topics):\n")
            for (s in subjects) {
                val topics = s.topics.take(budget.coerceAtLeast(0))
                budget -= topics.size
                append("- ").append(s.title).append(": ").append(topics.joinToString("; ") { it.title }.ifEmpty { "(no topics yet)" }).append('\n')
            }
        }.trimEnd()
    }

    fun forSession(subjects: List<SubjectRef>, summary: String, turns: List<ChatTurn>, preferences: List<String> = emptyList()): List<ChatMessage> = listOf(
        ChatMessage(Role.SYSTEM, system()),
        ChatMessage(
            Role.USER,
            buildString {
                append(library(subjects)).append("\n\n")
                if (preferences.isNotEmpty()) {
                    append("How the user likes things filed (from their past corrections):\n")
                    preferences.take(8).forEach { append("- ").append(it).append('\n') }
                    append('\n')
                }
                if (summary.isNotBlank()) append("Summary of the earlier part of the session:\n").append(summary.trim()).append("\n\n")
                append("Session:\n").append(ContextManager.transcript(turns)).append("\n\nWrite the entries now.")
            },
        ),
    )
}

/** Tolerant parser for the [FilingPrompts] format; small models drift, so every field is optional. */
object FilingParser {
    private val entryStart = Regex("""^(?:=+\s*)?(?:#+\s*)?\**ENTRY\b.*$""", RegexOption.IGNORE_CASE)
    private val field = Regex("""^\**(SUBJECT|TOPIC|TYPE|CONFIDENCE|PRIVACY|TITLE)\**\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<EntryProposal> {
        val blocks = ArrayList<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line0 in raw.lines()) {
            val line = line0.trim()
            if (entryStart.matches(line)) {
                current = ArrayList<String>().also { blocks += it }
                continue
            }
            if (current == null) {
                // Tolerate a model that skips the first "=== ENTRY" marker.
                if (field.find(line)?.groupValues?.get(1)?.uppercase() == "SUBJECT") current = ArrayList<String>().also { blocks += it } else continue
            } else if (field.find(line)?.groupValues?.get(1)?.uppercase() == "SUBJECT" && current.any { field.find(it.trim())?.groupValues?.get(1)?.uppercase() == "SUBJECT" }) {
                // A second SUBJECT line without a marker starts a new entry.
                current = ArrayList<String>().also { blocks += it }
            }
            current += line
        }
        return blocks.mapNotNull(::block)
    }

    private fun block(lines: List<String>): EntryProposal? {
        val fields = HashMap<String, String>()
        val body = ArrayList<String>()
        for (line in lines) {
            if (line.isEmpty()) continue
            val m = field.find(line)
            if (m != null && m.groupValues[1].uppercase() !in fields) {
                fields[m.groupValues[1].uppercase()] = m.groupValues[2].trim().trim('*', ' ')
            } else {
                body += line
            }
        }
        val bullets = body.map { it.replaceFirst(Regex("^(\\* |• )"), "- ") }
            .filterNot { stripBullet(it).let { c -> c.isEmpty() || c.equals("none", true) } }
        val title = fields["TITLE"]?.takeIf { it.isNotBlank() }
            ?: bullets.firstOrNull()?.let { stripBullet(it).take(60) }
            ?: return null
        if (bullets.isEmpty() && fields["TITLE"].isNullOrBlank()) return null
        return EntryProposal(
            subject = fields["SUBJECT"],
            topic = fields["TOPIC"],
            type = EntryType.parse(fields["TYPE"]),
            title = title.trimEnd('.'),
            body = bullets.joinToString("\n") { if (Regex("^(- |\\d+[.)] )").containsMatchIn(it)) it else "- $it" },
            modelSaysHigh = fields["CONFIDENCE"]?.lowercase()?.startsWith("high") == true,
            privacy = Privacy.parse(fields["PRIVACY"]),
        )
    }

    private fun stripBullet(s: String) = s.replaceFirst(Regex("^(- |\\* |• |\\d+[.)] )"), "").trim()
}

/**
 * v2 §6.3: file automatically only when confident; new subjects and topics are always confirmed by the user.
 * An entry is auto-filed only if the model said high confidence, the subject and topic both already exist
 * (and the topic belongs to that subject), the type parsed and the body is non-empty. Everything else goes to
 * the Inbox with the model's proposal pre-filled.
 */
object FilingPolicy {
    const val AUTO_FILE = 0.8
    /** Word-overlap similarity at which a proposed name is taken to mean an existing one. */
    const val NAME_MATCH = 0.75

    fun decide(p: EntryProposal, library: List<SubjectRef>, defaultPrivacy: Privacy = Privacy.DEFAULT): FilingDecision {
        val subjectName = CatchAll.clean(p.subject)
        val topicName = CatchAll.clean(p.topic)
        val subject = subjectName?.let { name -> match(name, library) { it.title } }
        val topicInSubject = subject?.let { s -> topicName?.let { name -> match(name, s.topics) { it.title } } }
        val topicElsewhere = if (topicInSubject == null && topicName != null) {
            library.flatMap { s -> s.topics.map { s to it } }.let { all -> match(topicName, all) { it.second.title } }
        } else null

        // The more private of the user's default and the model's suggestion: the model can only tighten privacy.
        val privacy = listOfNotNull(defaultPrivacy, p.privacy).minBy { it.ordinal }
        val type = p.type ?: EntryType.KNOWLEDGE

        val reasons = buildList {
            if (subjectName == null) add("no subject proposed") else if (subject == null) add("new subject \"$subjectName\"")
            if (topicName == null) add("no topic proposed")
            else if (topicInSubject == null && topicElsewhere != null) add("topic \"${topicElsewhere.second.title}\" is under ${topicElsewhere.first.title}")
            else if (topicInSubject == null) add("new topic \"$topicName\"")
            if (p.type == null) add("type unclear")
            if (!p.modelSaysHigh) add("low confidence")
            if (p.body.isBlank()) add("empty")
        }
        val confidence = if (reasons.isEmpty()) 0.9 else (0.6 - 0.1 * reasons.size).coerceAtLeast(0.1)
        val filed = confidence >= AUTO_FILE

        // Pre-fill the Inbox with existing records where the names matched, otherwise with the proposed names.
        // If the topic already lives under another subject, propose that pair so accepting it is consistent.
        val inboxSubject = topicElsewhere?.first ?: subject
        val inboxTopic = topicInSubject ?: topicElsewhere?.second
        return FilingDecision(
            status = if (filed) EntryStatus.FILED else EntryStatus.INBOX,
            subjectId = inboxSubject?.id,
            topicId = inboxTopic?.id,
            proposedSubject = inboxSubject?.title ?: subjectName,
            proposedTopic = inboxTopic?.title ?: topicName,
            type = type,
            privacy = privacy,
            title = p.title,
            body = p.body,
            confidence = confidence,
            reason = if (filed) "" else reasons.joinToString("; ").replaceFirstChar { it.uppercase() },
        )
    }

    /** Exact slug match first, then the best word-overlap match at or above [NAME_MATCH]. */
    fun <T> match(name: String, candidates: List<T>, title: (T) -> String): T? {
        val slug = Slug.of(name)
        candidates.firstOrNull { Slug.of(title(it)) == slug }?.let { return it }
        return candidates.map { it to TopicMatcher.similarity(name, title(it)) }
            .filter { it.second >= NAME_MATCH }
            .maxByOrNull { it.second }?.first
    }
}
