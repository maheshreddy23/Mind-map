package com.maheshreddy.mym.core.library

import com.maheshreddy.mym.core.Slug

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
