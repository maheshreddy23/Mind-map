package com.maheshreddy.mym.core.library

import com.maheshreddy.mym.core.Slug

/** requirements-v2 §6.2. [label] is what the user sees and what the filing prompt uses. */
enum class EntryType(val label: String, vararg val aliases: String) {
    KNOWLEDGE("Knowledge", "fact", "facts", "procedure", "how-to", "concept"),
    LESSON("Lesson", "lessons", "lesson learned", "mistake", "pitfall"),
    PRINCIPLE("Principle", "principles", "rule of thumb", "judgement", "judgment"),
    IDEA("Idea", "ideas", "future"),
    REFLECTION("Reflection", "reflections", "personal", "thought"),
    OPEN_QUESTION("Open question", "open questions", "question", "unresolved");

    companion object {
        fun parse(text: String?): EntryType? {
            val t = text?.lowercase()?.trim()?.trim('*', '.', ' ', ':') ?: return null
            if (t.isEmpty()) return null
            return entries.firstOrNull { it.label.lowercase() == t || it.name.lowercase() == t }
                ?: entries.firstOrNull { e -> e.aliases.any { t == it } }
                ?: entries.firstOrNull { e -> t.startsWith(e.label.lowercase()) }
        }
    }
}

/** requirements-v2 §8. Private never leaves the phone; only Shareable entries can be exported to others. */
enum class Privacy(val label: String) {
    PRIVATE("Private"),
    PERSONAL("Personal"),
    SHAREABLE("Shareable");

    companion object {
        /** v2 decision 4. */
        val DEFAULT = PRIVATE

        fun parse(text: String?): Privacy? {
            val t = text?.lowercase()?.trim()?.trim('*', '.', ' ') ?: return null
            return entries.firstOrNull { it.label.lowercase() == t || it.name.lowercase() == t }
        }
    }
}

/** Where an entry sits in the filing flow. */
enum class EntryStatus { FILED, INBOX }

/** v2 §6.3: never a catch-all bucket. Names like these are treated as "no proposal". */
object CatchAll {
    private val names = setOf(
        "general", "misc", "miscellaneous", "other", "others", "uncategorized", "uncategorised", "unsorted",
        "random", "notes", "note", "stuff", "various", "untitled", "none", "n a", "na", "unknown", "topic", "subject",
    )

    fun isCatchAll(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        return Slug.of(name).replace('-', ' ') in names
    }

    /** Returns the trimmed name, or null when it is blank or a catch-all. */
    fun clean(name: String?): String? = name?.trim()?.trim('*', '"', '.', ' ')?.takeUnless { isCatchAll(it) }
}
