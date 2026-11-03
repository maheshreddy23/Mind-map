package com.maheshreddy.mym.core.library

/** A suggested gap in the library ("No entries on ECO yet") with what to talk about to fill it. */
data class GapSuggestion(val text: String, val talkAbout: String)

object Gaps {
    /**
     * Up to [limit] gaps: domain words the user speaks (their vocabulary) that no entry mentions yet, topics with
     * open questions, and thin topics (one entry). [topics] are (title, entries, open questions).
     */
    fun suggest(vocabulary: List<String>, entryTexts: List<String>, topics: List<Triple<String, Int, Int>>, limit: Int = 3): List<GapSuggestion> {
        val text = entryTexts.joinToString("\n").lowercase()
        val words = Regex("[\\p{L}\\p{N}]+").findAll(text).map { it.value }.toSet()
        val missing = vocabulary.filter { v ->
            val k = v.lowercase().trim()
            k.isNotEmpty() && if (k.contains(' ')) k !in text else k !in words && (k + "s") !in words
        }.map { GapSuggestion("No entries on $it yet", it) }
        val open = topics.filter { it.third > 0 }.map {
            GapSuggestion("${it.first}: ${if (it.third == 1) "an open question" else "${it.third} open questions"} to settle", it.first)
        }
        val thin = topics.filter { it.second == 1 && it.third == 0 }.map { GapSuggestion("Only one entry on ${it.first}", it.first) }
        // Interleave so one kind does not crowd out the others.
        val out = ArrayList<GapSuggestion>()
        val lists = listOf(missing, open, thin)
        var i = 0
        while (out.size < limit && lists.any { i < it.size }) {
            lists.forEach { if (i < it.size && out.size < limit) out += it[i] }
            i++
        }
        return out
    }

    /** A shelf's progress: the share of its topics that are not gaps (more than one entry, nothing open). */
    fun progress(topics: List<Pair<Int, Int>>): Float =
        if (topics.isEmpty()) 0f else topics.count { (entries, open) -> entries > 1 && open == 0 }.toFloat() / topics.size
}
