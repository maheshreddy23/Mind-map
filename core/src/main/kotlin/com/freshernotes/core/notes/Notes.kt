package com.freshernotes.core.notes

import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Slug
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.ContextManager
import com.freshernotes.core.llm.Role

/** The note sections required by the spec, in page order. */
enum class Section(val heading: String, private vararg val aliases: String) {
    CONCEPTS("Key concepts explained", "key concepts", "concepts"),
    PROCEDURES("Procedures or steps", "procedures", "steps", "procedure"),
    RATIONALE("Reasons and rationale", "reasons", "rationale", "why"),
    PITFALLS("Pitfalls and lessons learned", "pitfalls", "lessons learned", "mistakes"),
    OPEN_QUESTIONS("Open questions to revisit", "open questions", "questions to revisit");

    fun matches(heading: String): Boolean {
        val h = heading.lowercase().trim().trim('#', ' ', ':', '*')
        return h == this.heading.lowercase() || aliases.any { h == it || h.startsWith(it) }
    }

    companion object {
        fun of(heading: String): Section? = entries.firstOrNull { it.matches(heading) }
    }
}

/** Structured notes for one session. */
data class SessionNote(
    val topic: String,
    val subtopics: List<String>,
    val sections: Map<Section, List<String>>,
) {
    fun toMarkdown(title: String? = null): String = buildString {
        append("# ").append(title ?: topic).append("\n\n")
        if (subtopics.isNotEmpty()) append("**Sub-topics:** ").append(subtopics.joinToString(", ")).append("\n\n")
        for (s in Section.entries) {
            append("## ").append(s.heading).append('\n')
            val items = sections[s].orEmpty()
            if (items.isEmpty()) append("- _(nothing recorded)_\n") else items.forEach { append(it.toBullet()).append('\n') }
            append('\n')
        }
    }.trimEnd() + "\n"
}

internal fun String.toBullet(): String {
    val t = trim()
    return if (Regex("^(- |\\* |\\d+[.)] )").containsMatchIn(t)) t.replaceFirst(Regex("^\\* "), "- ") else "- $t"
}

object NotePrompts {
    const val SYSTEM =
        "You turn a conversation between a senior aerospace engineer and a fresher into detailed, well organized " +
            "study notes. Only use what the senior actually said. Keep tool names, numbers and reasons exactly. " +
            "Write in plain English, as short bullet points. Use exactly this format:\n" +
            "TOPIC: <main topic, 2-5 words>\n" +
            "SUBTOPICS: <comma separated sub-topics>\n" +
            "## Key concepts explained\n- ...\n" +
            "## Procedures or steps\n1. ...\n" +
            "## Reasons and rationale\n- ...\n" +
            "## Pitfalls and lessons learned\n- ...\n" +
            "## Open questions to revisit\n- ...\n" +
            "If a section has nothing, write \"- none\"."

    fun forSession(summary: String, turns: List<ChatTurn>): List<ChatMessage> = listOf(
        ChatMessage(Role.SYSTEM, SYSTEM),
        ChatMessage(
            Role.USER,
            buildString {
                if (summary.isNotBlank()) append("Notes from the earlier part of the session:\n").append(summary.trim()).append("\n\n")
                append("Conversation:\n").append(ContextManager.transcript(turns)).append("\n\nWrite the notes now.")
            },
        ),
    )
}

/** Parses the LLM's notes output; tolerant of missing sections, numbering and heading variants. */
object NoteParser {
    fun parse(raw: String, fallbackTopic: String = "General"): SessionNote {
        var topic: String? = null
        var subtopics = emptyList<String>()
        val sections = LinkedHashMap<Section, MutableList<String>>()
        var current: Section? = null
        for (line0 in raw.lines()) {
            val line = line0.trim()
            if (line.isEmpty()) continue
            val kv = Regex("""^\**(TOPIC|SUBTOPICS|SUB-TOPICS)\**\s*:\s*(.*)$""", RegexOption.IGNORE_CASE).find(line)
            if (kv != null) {
                val value = kv.groupValues[2].trim().trim('*', ' ')
                if (kv.groupValues[1].equals("TOPIC", true)) topic = value
                else subtopics = value.split(',', ';').map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() && !it.equals("none", true) }
                continue
            }
            if (line.startsWith("#") || (line.startsWith("**") && line.endsWith("**"))) {
                current = Section.of(line.trim('#', '*', ' ', ':'))
                continue
            }
            val sec = current ?: continue
            val item = line.toBullet()
            val content = item.replaceFirst(Regex("^(- |\\d+[.)] )"), "").trim()
            if (content.isEmpty() || content.equals("none", true) || content.equals("n/a", true)) continue
            sections.getOrPut(sec) { ArrayList() } += item
        }
        return SessionNote(
            topic = topic?.takeIf { it.isNotBlank() }?.let(::titleCase) ?: fallbackTopic,
            subtopics = subtopics.distinctBy { it.lowercase() },
            sections = sections,
        )
    }

    private fun titleCase(s: String) = s.trim().trimEnd('.').replaceFirstChar { it.uppercase() }
}

/** Matches a new topic name against existing topic titles. */
object TopicMatcher {
    private val stop = setOf("and", "the", "of", "in", "for", "a", "an", "to", "with", "on")

    fun words(title: String): Set<String> =
        Slug.of(title).split('-').filter { it.isNotEmpty() && it !in stop }.map { stem(it) }.toSet()

    private fun stem(w: String) = if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) w.dropLast(1) else w

    fun similarity(a: String, b: String): Double {
        val wa = words(a); val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return wa.intersect(wb).size.toDouble() / wa.union(wb).size
    }

    /** Returns the best existing title with similarity >= [threshold], or null for a new topic. */
    fun bestMatch(topic: String, existing: List<String>, threshold: Double = 0.5): String? =
        existing.map { it to similarity(topic, it) }.filter { it.second >= threshold }.maxByOrNull { it.second }?.first
}

/**
 * Merges session notes into a topic's Markdown page. Bullets already present (ignoring case and punctuation)
 * are not duplicated; sections the user added by hand are preserved; a session log is kept at the bottom.
 */
object TopicPageMerger {
    private const val SESSIONS = "Sessions"

    fun merge(existingPage: String, title: String, note: SessionNote, sessionLabel: String): String {
        val parsed = parsePage(existingPage)
        val subtopics = (parsed.subtopics + note.subtopics).distinctBy { it.lowercase() }
        val out = StringBuilder()
        out.append("# ").append(title).append("\n\n")
        if (subtopics.isNotEmpty()) out.append("**Sub-topics:** ").append(subtopics.joinToString(", ")).append("\n\n")
        for (s in Section.entries) {
            val old = parsed.sections[s.heading].orEmpty().filterNot { it.contains("_(nothing recorded)_") }
            val seen = old.map(::norm).toMutableSet()
            val added = note.sections[s].orEmpty().filter { seen.add(norm(it)) }
            val items = old + added.map { it.toBullet() }
            out.append("## ").append(s.heading).append('\n')
            if (items.isEmpty()) out.append("- _(nothing recorded)_\n") else items.forEach { out.append(it).append('\n') }
            out.append('\n')
        }
        for ((heading, lines) in parsed.sections) {
            if (Section.entries.any { it.heading == heading } || heading == SESSIONS) continue
            out.append("## ").append(heading).append('\n')
            lines.forEach { out.append(it).append('\n') }
            out.append('\n')
        }
        out.append("## ").append(SESSIONS).append('\n')
        val log = parsed.sections[SESSIONS].orEmpty() + "- $sessionLabel"
        log.distinct().forEach { out.append(it).append('\n') }
        return out.toString()
    }

    private data class Page(val subtopics: List<String>, val sections: LinkedHashMap<String, MutableList<String>>)

    private fun parsePage(md: String): Page {
        var subtopics = emptyList<String>()
        val sections = LinkedHashMap<String, MutableList<String>>()
        var current: String? = null
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("**Sub-topics:**") ->
                    subtopics = line.removePrefix("**Sub-topics:**").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                line.startsWith("## ") -> {
                    val h = line.removePrefix("## ").trim()
                    current = Section.of(h)?.heading ?: h
                    sections.getOrPut(current) { ArrayList() }
                }
                line.startsWith("# ") -> current = null
                line.isBlank() -> Unit
                else -> current?.let { sections.getOrPut(it) { ArrayList() } += line }
            }
        }
        return Page(subtopics, sections)
    }

    private fun norm(s: String) = s.lowercase().replaceFirst(Regex("^(- |\\d+[.)] )"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
}
