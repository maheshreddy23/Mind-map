package com.freshernotes.core.library

import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.Role

/** An entry offered to the model as a numbered source. */
data class AskSource(val entryId: Long, val title: String, val body: String, val topic: String?)

/** A piece of the answer: plain text, or a citation of source [number] (1-based). */
sealed interface AnswerPart {
    data class Text(val text: String) : AnswerPart
    data class Cite(val number: Int, val entryId: Long) : AnswerPart
}

/**
 * Ask the library (requirements-v2 §7): answer only from the user's own entries and cite them. Retrieval is
 * keyword (FTS5) for now; semantic search with an embedding model comes in phase 3 and plugs in before this.
 */
object AskPrompt {
    const val MAX_SOURCES = 5
    private const val MAX_BODY = 600

    private val stop = setOf(
        "what", "whats", "have", "has", "had", "i", "me", "my", "mine", "we", "our", "you", "your", "said", "say", "says",
        "about", "the", "a", "an", "of", "on", "in", "to", "for", "and", "or", "is", "are", "was", "were", "be", "been",
        "do", "does", "did", "how", "why", "when", "where", "which", "who", "whom", "it", "its", "that", "this", "these",
        "those", "there", "with", "from", "at", "by", "as", "if", "can", "could", "should", "would", "will", "tell",
        "anything", "something", "know", "think", "after", "before", "any", "all", "ever", "us",
    )

    /** Content words of a question, for an OR full-text query ("What have I said about datum selection?" → datum, selection). */
    fun keywords(question: String): List<String> =
        question.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in stop }.distinct().take(8)

    fun messages(name: String, question: String, sources: List<AskSource>): List<ChatMessage> = listOf(
        ChatMessage(
            Role.SYSTEM,
            "You are $name, the user's librarian. Answer the user's question using ONLY the numbered entries from their " +
                "own library. Cite each statement with the entry number in square brackets, like [1] or [2]. " +
                "Be brief: 1-4 sentences. If the entries only partly answer, say what is missing. " +
                "If they do not answer the question at all, reply exactly: NOTHING",
        ),
        ChatMessage(
            Role.USER,
            buildString {
                append("Entries:\n")
                sources.forEachIndexed { i, s ->
                    append('[').append(i + 1).append("] ").append(s.title)
                    s.topic?.let { append(" (").append(it).append(')') }
                    append(": ").append(s.body.replace(Regex("\\s+"), " ").take(MAX_BODY)).append('\n')
                }
                append("\nQuestion: ").append(question.trim())
            },
        ),
    )

    /** True when the model says the library has nothing on it. */
    fun isNothing(answer: String) = answer.trim().trim('.', '"', '*').equals("NOTHING", ignoreCase = true) || answer.isBlank()

    /** Splits "A [1], then B [2][3]." into text and citation parts; citations to unknown sources are dropped. */
    fun parse(answer: String, sources: List<AskSource>): List<AnswerPart> {
        val parts = ArrayList<AnswerPart>()
        var last = 0
        val re = Regex("""\[(\d+(?:\s*,\s*\d+)*)]""")
        for (m in re.findAll(answer)) {
            // Citations sit flush against the word they support: "PLM [1]," -> "PLM" + chip + ",".
            val before = answer.substring(last, m.range.first).trimEnd()
            if (before.isNotEmpty()) parts += AnswerPart.Text(before)
            m.groupValues[1].split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { n ->
                sources.getOrNull(n - 1)?.let { parts += AnswerPart.Cite(n, it.entryId) }
            }
            last = m.range.last + 1
        }
        if (last < answer.length) parts += AnswerPart.Text(answer.substring(last))
        // Tidy spacing left by removed or adjacent citations: "word [1] ." -> "word [1]."
        return parts.map { if (it is AnswerPart.Text) AnswerPart.Text(it.text.replace(Regex("\\s+([.,;:])"), "$1")) else it }
    }

    /** Distinct sources actually cited (what "From N of your entries" counts). */
    fun cited(parts: List<AnswerPart>): List<Long> = parts.filterIsInstance<AnswerPart.Cite>().map { it.entryId }.distinct()
}
