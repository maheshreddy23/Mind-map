package com.freshernotes.core.llm

/** requirements-v2 §4.1 / decision 3: how the butler interviews. */
enum class InterviewStyle(val label: String, val hint: String, val instruction: String) {
    SHARP_ENGINEER(
        "Sharp engineer",
        "Strong fundamentals, new to your company. Challenges vague answers.",
        "In interviews you act as a sharp engineer with M.Tech-level fundamentals who is new to the user's company " +
            "and its practices. Ask probing why, how and what-if questions, challenge vague or hand-wavy answers, " +
            "and still ask for the explanations a newcomer needs (company jargon, tools, process).",
    ),
    CURIOUS_FRESHER(
        "Curious fresher",
        "Asks the basics a newcomer needs. Great for teaching juniors.",
        "In interviews you act as an eager, humble fresher who just joined: clarify jargon, ask for reasons " +
            "(why is it done this way?), examples, mistakes to avoid, and how things connect.",
    ),
    SCEPTICAL_REVIEWER(
        "Sceptical reviewer",
        "Pushes back on every claim. Good for stress-testing lessons.",
        "In interviews you act as a sceptical reviewer: question assumptions, ask for evidence and numbers, " +
            "failure modes, and what was tried that did not work.",
    );

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name == s } ?: SHARP_ENGINEER
    }
}

/**
 * The editable persona profile (§4.2-4.3). Sliders run 0..1 from the left word to the right word:
 * formal→casual, reserved→warm, serious→witty, brief→detailed. Defaults are decision 2's "M".
 */
data class ButlerProfile(
    val name: String = DEFAULT_NAME,
    val callsYou: String = "",
    val casual: Float = 0.3f,
    val warm: Float = 0.55f,
    val witty: Float = 0.65f,
    val detailed: Float = 0.25f,
    val style: InterviewStyle = InterviewStyle.SHARP_ENGINEER,
    val quirks: String = "",
) {
    val displayName: String get() = name.trim().ifEmpty { DEFAULT_NAME }
    val initial: String get() = displayName.first().uppercase()

    companion object {
        const val DEFAULT_NAME = "M"
    }
}

/**
 * Builds the butler's system prompt from the profile. It depends only on the profile and the reply length, so it
 * is identical for every turn of a session: llama.cpp's prefix KV cache reuses it and first-token time does not
 * grow with the persona (v1 baseline 1848 ms warm).
 */
object ButlerPrompt {
    private fun pick(v: Float, low: String, mid: String, high: String) = when {
        v < 0.34f -> low
        v < 0.67f -> mid
        else -> high
    }

    /** "Calm, dry-witted, one step ahead." style one-liner for the Butler screen. */
    fun summary(p: ButlerProfile): String {
        val parts = listOf(
            pick(p.casual, "formal", "polished", "relaxed"),
            pick(p.warm, "reserved", "kind", "warm"),
            pick(p.witty, "earnest", "quietly amused", "dry-witted"),
            pick(p.detailed, "brief", "measured", "thorough"),
        )
        return "Calm, " + parts.joinToString(", ") + ", one step ahead."
    }

    fun system(p: ButlerProfile, maxWords: Int): String = buildString {
        append("You are ").append(p.displayName).append(", the user's personal butler and librarian. ")
        append("You are an original character: calm, unflappable, anticipatory and efficient. ")
        append("Your manner is ")
        append(pick(p.casual, "formal", "polite and polished", "relaxed and casual")).append(", ")
        append(pick(p.warm, "reserved", "kind", "warm")).append(", ")
        append(pick(p.witty, "serious", "lightly witty", "dry-witted")).append(" and ")
        append(pick(p.detailed, "brief", "measured", "detailed")).append(". ")
        if (p.callsYou.isNotBlank()) append("Address the user as \"").append(p.callsYou.trim()).append("\". ")
        if (p.quirks.isNotBlank()) append("Character notes: ").append(p.quirks.trim().take(300)).append(' ')
        append("The user talks about what they know; you help capture it. ")
        append(p.style.instruction).append(' ')
        append("Ask ONE short, specific question at a time. Never lecture. Keep replies under ").append(maxWords).append(" words. ")
        append("When the user says \"wrap up\", stop asking questions.")
    }

    /** End-of-prompt reminder; small models drift into summarizing without it. */
    fun nudge(p: ButlerProfile, maxWords: Int, strict: Boolean = false): String =
        if (strict) "(You are ${p.displayName}. Do NOT summarize or explain. Reply with exactly ONE short question ending in \"?\", under $maxWords words.)"
        else "(Reply as ${p.displayName}: ask ONE short, specific question, under $maxWords words.)"
}
