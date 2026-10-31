package com.freshernotes.core.audio

object TranscriptFilter {
    private val bracketed = Regex("""[\[(][^\])]*[\])]""")
    // Whisper hallucinates these on silence or noise.
    private val hallucinations = setOf("you", "thank you", "thanks for watching", "bye")

    /** Strips non-speech tags like [BLANK_AUDIO] or (music) and collapses whitespace. */
    fun clean(text: String): String = text.replace(bracketed, " ").replace(Regex("\\s+"), " ").trim()

    fun isMeaningful(text: String): Boolean {
        val t = clean(text).lowercase().trim { !it.isLetterOrDigit() }
        return t.isNotEmpty() && t !in hallucinations
    }
}
