package com.freshernotes.core.library

/** An entry as it appears on a topic page. */
data class PageEntry(val id: Long, val title: String, val type: EntryType, val body: String, val createdAt: Long)

/**
 * Builds a topic page from its filed entries (v2 §6.4): grouped by type in a fixed order, numbered 1..n across
 * the page, with bullets that already appeared earlier on the page removed. Deterministic and instant, so it
 * runs after every move, merge, split, rename or delete. The butler's optional prose [summary] goes on top.
 */
object PageBuilder {
    fun build(title: String, subject: String?, summary: String, entries: List<PageEntry>): String = buildString {
        append("# ").append(title).append("\n\n")
        if (!subject.isNullOrBlank()) append("_").append(subject).append("_\n\n")
        if (summary.isNotBlank()) append(summary.trim()).append("\n\n")
        if (entries.isEmpty()) {
            append("_No entries yet._\n")
            return@buildString
        }
        val seen = HashSet<String>()
        var n = 0
        for (type in EntryType.entries) {
            val group = entries.filter { it.type == type }.sortedBy { it.createdAt }
            if (group.isEmpty()) continue
            append("## ").append(plural(type)).append("\n\n")
            for (e in group) {
                n++
                append("### ").append(n).append(". ").append(e.title.trim()).append('\n')
                val lines = e.body.lines().map { it.trimEnd() }.map(::demoteHeading).filter { line ->
                    val key = norm(line)
                    // Keep structure lines (blank, bold labels); drop a bullet already stated earlier on the page.
                    key.isEmpty() || line.startsWith("**") || seen.add(key)
                }
                val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
                append(if (text.isEmpty() || text.lines().all { it.isBlank() || it.startsWith("**") }) "_(covered above)_" else text)
                append("\n\n")
            }
        }
    }.trimEnd() + "\n"

    fun plural(type: EntryType) = when (type) {
        EntryType.KNOWLEDGE -> "Knowledge"
        EntryType.LESSON -> "Lessons"
        EntryType.PRINCIPLE -> "Principles"
        EntryType.IDEA -> "Ideas"
        EntryType.REFLECTION -> "Reflections"
        EntryType.OPEN_QUESTION -> "Open questions"
    }

    /** Entry bodies may carry their own headings; keep them below the page's ### level. */
    private fun demoteHeading(line: String): String {
        val m = Regex("^#{1,6}\\s+(.*)$").find(line) ?: return line
        return "**" + m.groupValues[1].trim() + "**"
    }

    internal fun norm(s: String) = s.lowercase().replaceFirst(Regex("^\\s*(- |\\* |\\d+[.)] )"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
}
