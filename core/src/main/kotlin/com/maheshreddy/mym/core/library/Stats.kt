package com.maheshreddy.mym.core.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Library stats for the Inbox empty state: entries this week, topics, and the capture streak. */
data class LibraryStats(val entriesThisWeek: Int, val topics: Int, val streakDays: Int)

object Stats {
    /**
     * [created] are entry capture times. The streak counts consecutive days with at least one entry, ending today
     * (or yesterday, so the streak is not "broken" before today's session).
     */
    fun of(created: List<Long>, topics: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): LibraryStats {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val days = created.map { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone) }.toSet()
        val weekStart = today.minusDays(6)
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) { streak++; day = day.minusDays(1) }
        return LibraryStats(created.count { !LocalDate.ofInstant(Instant.ofEpochMilli(it), zone).isBefore(weekStart) }, topics, streak)
    }
}
