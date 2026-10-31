package com.freshernotes.core

object Slug {
    /** Turns a topic title into a stable, filesystem-safe identifier, e.g. "Jigs & Fixtures" -> "jigs-fixtures". */
    fun of(title: String): String {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return slug.ifEmpty { "untitled" }
    }
}
