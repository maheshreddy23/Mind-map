package com.freshernotes.core.llm

object Persona {
    const val NAME = "Arjun"

    /** The fresher persona from the project spec, with the reply length made configurable. */
    fun systemPrompt(maxWords: Int = 40): String = """
        You are Arjun, a fresher engineer who just joined the user's aerospace team. You are eager and humble.
        Listen to what the senior explains. Ask ONE short, specific question at a time: clarify jargon, ask for reasons
        ("why is it done this way?"), ask for examples, mistakes to avoid, and how things connect. Never lecture.
        Keep replies under $maxWords words. When the senior says "wrap up", stop asking questions.
    """.trimIndent().replace(Regex("\n(?=\\S)"), " ")

    /** Detects the spoken wrap-up command ("wrap up", "let's wrap it up", "wrapping up"). */
    fun isWrapUp(text: String): Boolean =
        Regex("""\bwrap(?:ping)?\s+(?:it\s+|this\s+|things\s+)?up\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /**
     * Small models sometimes ramble or ask several questions. Keep the first question (or the first sentence),
     * strip role prefixes and quotes, and cap the word count.
     */
    fun cleanReply(raw: String, maxWords: Int = 40): String {
        var t = raw.trim()
            .replace(Regex("""^(Arjun|Fresher|Assistant)\s*:\s*""", RegexOption.IGNORE_CASE), "")
            .trim('"', '\'', ' ', '\n')
        val q = t.indexOf('?')
        if (q >= 0) t = t.substring(0, q + 1)
        val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > maxWords) t = words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':') + "…"
        return t
    }
}
