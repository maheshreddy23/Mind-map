package com.maheshreddy.mym.core.llm

object Persona {
    const val NAME = "Arjun"

    /** The fresher persona from the project spec, with the reply length made configurable. */
    fun systemPrompt(maxWords: Int = 40): String = """
        You are Arjun, a fresher engineer who just joined the user's aerospace team. You are eager and humble.
        Listen to what the senior explains. Ask ONE short, specific question at a time: clarify jargon, ask for reasons
        ("why is it done this way?"), ask for examples, mistakes to avoid, and how things connect. Never lecture.
        Keep replies under $maxWords words. When the senior says "wrap up", stop asking questions.
    """.trimIndent().replace(Regex("\n(?=\\S)"), " ")

    /**
     * Reminder appended to the latest senior turn. Small models drift into summarizing; a short instruction at the
     * end of the prompt keeps them in character far more reliably than the system prompt alone.
     */
    fun nudge(maxWords: Int, strict: Boolean = false): String =
        if (strict) "(You are Arjun. Do NOT summarize or explain. Reply with exactly ONE short question ending in \"?\", under $maxWords words.)"
        else "(Reply as Arjun: ask ONE short, specific question, under $maxWords words.)"

    /** A cleaned reply counts as a question if it ends with a question mark. */
    fun isQuestion(reply: String): Boolean = reply.trim().endsWith("?")

    private val fallbacks = listOf(
        "Could you give me a concrete example of that?",
        "Why is it done this way and not another way?",
        "What mistakes do new engineers usually make here?",
        "How does this connect to what you explained earlier?",
        "What does that term mean exactly?",
    )

    /** Used only when the model fails twice to produce a question, so the conversation never stalls. */
    fun fallbackQuestion(turnIndex: Int): String = fallbacks[Math.floorMod(turnIndex, fallbacks.size)]

    /** Detects the spoken wrap-up command ("wrap up", "let's wrap it up", "wrapping up"). */
    fun isWrapUp(text: String): Boolean =
        Regex("""\bwrap(?:ping)?\s+(?:it\s+|this\s+|things\s+)?up\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /**
     * Small models sometimes ramble or ask several questions. Keep the first question (or the first sentence),
     * strip role prefixes and quotes, and cap the word count.
     */
    fun cleanReply(raw: String, maxWords: Int = 40, name: String = NAME): String {
        var t = raw.trim()
            .replace(Regex("""^(Arjun|Fresher|Assistant|Butler|${Regex.escape(name)})\s*:\s*""", RegexOption.IGNORE_CASE), "")
            .trim('"', '\'', ' ', '\n')
        val q = t.indexOf('?')
        if (q >= 0) t = t.substring(0, q + 1)
        val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > maxWords) t = words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':') + "…"
        return t
    }
}
