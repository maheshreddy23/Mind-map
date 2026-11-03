package com.maheshreddy.mym.core.library

/** What the nudge engine looks at (all from the user's own library). */
data class NudgeInput(
    val now: Long,
    val startOfToday: Long,
    val snoozedUntil: Long,
    /** Topics with their open-question counts. */
    val topics: List<NudgeTopic>,
    /** The oldest filed entry, if any, for spaced review. */
    val oldest: NudgeEntry?,
    val sessionsToday: Int,
    /** Nudge types switched on in Settings. */
    val enabled: Set<Nudge.Kind> = Nudge.Kind.entries.toSet(),
)

data class NudgeTopic(val id: Long, val title: String, val openQuestions: Int)
data class NudgeEntry(val id: Long, val title: String, val topic: String?, val createdAt: Long)

/** One nudge for the Home card. [talkAbout] titles the session that "Talk now" starts. */
data class Nudge(val kind: Kind, val text: String, val talkAbout: String?) {
    enum class Kind { UNFINISHED_TOPIC, RESURFACE, DAILY_CAPTURE }
}

/**
 * requirements-v2 §9, on-device and deterministic: picks today's single nudge. Unfinished topics come first,
 * then spaced review of something said a month or more ago, then the daily capture prompt. "Later" snoozes
 * until tomorrow. (Inbox reminders have their own strip on Home; notifications arrive with the nudges phase.)
 */
object NudgeEngine {
    private const val DAY = 24L * 60 * 60 * 1000
    const val RESURFACE_AFTER_DAYS = 30

    fun pick(input: NudgeInput): Nudge? {
        if (input.now < input.snoozedUntil) return null
        if (Nudge.Kind.UNFINISHED_TOPIC in input.enabled) input.topics.filter { it.openQuestions > 0 }.maxWithOrNull(compareBy({ it.openQuestions }, { -it.id }))?.let { t ->
            val q = if (t.openQuestions == 1) "an open question" else "${t.openQuestions} open questions"
            return Nudge(Nudge.Kind.UNFINISHED_TOPIC, "${t.title} still has $q. Ten minutes on it?", t.title)
        }
        if (Nudge.Kind.RESURFACE in input.enabled) input.oldest?.takeIf { input.now - it.createdAt >= RESURFACE_AFTER_DAYS * DAY }?.let { e ->
            return Nudge(Nudge.Kind.RESURFACE, "${ago(input.now - e.createdAt)} you said \"${e.title}\". Still true?", e.topic ?: e.title)
        }
        if (Nudge.Kind.DAILY_CAPTURE in input.enabled && input.sessionsToday == 0) return Nudge(Nudge.Kind.DAILY_CAPTURE, "Anything worth keeping from today?", null)
        return null
    }

    fun ago(ms: Long): String {
        val days = ms / DAY
        return when {
            days >= 365 -> if (days < 730) "A year ago" else "${days / 365} years ago"
            days >= 60 -> "${days / 30} months ago"
            days >= 30 -> "A month ago"
            else -> "$days days ago"
        }.let { s ->
            // Spell small numbers like the design ("Three months ago").
            val words = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven")
            Regex("^(\\d+) ").find(s)?.let { m -> m.groupValues[1].toInt().takeIf { it in 1..11 }?.let { s.replaceFirst(m.value, words[it] + " ") } } ?: s
        }
    }

    /** "Later": hide the card until the start of tomorrow. */
    fun snoozeUntil(startOfToday: Long) = startOfToday + DAY

    /** Minutes after midnight inside quiet hours (which may wrap past midnight)? */
    fun isQuiet(minute: Int, quietStart: Int, quietEnd: Int): Boolean =
        if (quietStart == quietEnd) false
        else if (quietStart < quietEnd) minute in quietStart until quietEnd
        else minute >= quietStart || minute < quietEnd

    /** The daily nudge time, moved to the end of quiet hours if it falls inside them. */
    fun notifyAt(nudgeTime: Int, quietStart: Int, quietEnd: Int): Int = if (isQuiet(nudgeTime, quietStart, quietEnd)) quietEnd else nudgeTime
}
