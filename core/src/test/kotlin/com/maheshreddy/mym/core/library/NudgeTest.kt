package com.maheshreddy.mym.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgeTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day
    private fun input(topics: List<NudgeTopic> = emptyList(), oldest: NudgeEntry? = null, today: Int = 0, snoozed: Long = 0) =
        NudgeInput(now, now - 3_600_000, snoozed, topics, oldest, today)

    @Test
    fun unfinishedTopicComesFirst() {
        val n = NudgeEngine.pick(input(listOf(NudgeTopic(1, "Kickoff", 1), NudgeTopic(2, "Datums", 2)), NudgeEntry(9, "Old", null, now - 100 * day)))!!
        assertEquals(Nudge.Kind.UNFINISHED_TOPIC, n.kind)
        assertEquals("Datums still has 2 open questions. Ten minutes on it?", n.text)
        assertEquals("Datums", n.talkAbout)
    }

    @Test
    fun resurfacesSomethingSaidLongAgo() {
        val n = NudgeEngine.pick(input(oldest = NudgeEntry(9, "Jigs locate on tooling holes", "Drill jigs", now - 95 * day), today = 1))!!
        assertEquals(Nudge.Kind.RESURFACE, n.kind)
        assertEquals("Three months ago you said \"Jigs locate on tooling holes\". Still true?", n.text)
        assertEquals("Drill jigs", n.talkAbout)
        assertNull("too recent to resurface", NudgeEngine.pick(input(oldest = NudgeEntry(9, "x", null, now - 5 * day), today = 1)))
    }

    @Test
    fun dailyCaptureWhenNothingElseAndNoSessionToday() {
        assertEquals(Nudge.Kind.DAILY_CAPTURE, NudgeEngine.pick(input())!!.kind)
        assertNull(NudgeEngine.pick(input(today = 1)))
    }

    @Test
    fun laterSnoozesUntilTomorrow() {
        assertNull(NudgeEngine.pick(input(snoozed = now + 1)))
        assertEquals(now - 3_600_000 + day, NudgeEngine.snoozeUntil(now - 3_600_000))
    }

    @Test
    fun agoReadsNaturally() {
        assertEquals("A month ago", NudgeEngine.ago(40 * day))
        assertEquals("Six months ago", NudgeEngine.ago(185 * day))
        assertEquals("A year ago", NudgeEngine.ago(400 * day))
    }
}

class NudgeSettingsTest {
    @org.junit.Test
    fun typesCanBeSwitchedOff() {
        val day = 24L * 60 * 60 * 1000
        val input = NudgeInput(1000 * day, 1000 * day - 1, 0, listOf(NudgeTopic(1, "Datums", 1)), null, 0, enabled = setOf(Nudge.Kind.DAILY_CAPTURE))
        org.junit.Assert.assertEquals(Nudge.Kind.DAILY_CAPTURE, NudgeEngine.pick(input)!!.kind)
        org.junit.Assert.assertNull(NudgeEngine.pick(input.copy(enabled = emptySet())))
    }

    @org.junit.Test
    fun quietHoursWrapPastMidnight() {
        org.junit.Assert.assertTrue(NudgeEngine.isQuiet(23 * 60, 22 * 60, 7 * 60))
        org.junit.Assert.assertTrue(NudgeEngine.isQuiet(3 * 60, 22 * 60, 7 * 60))
        org.junit.Assert.assertFalse(NudgeEngine.isQuiet(19 * 60, 22 * 60, 7 * 60))
        org.junit.Assert.assertEquals(7 * 60, NudgeEngine.notifyAt(6 * 60, 22 * 60, 7 * 60))
        org.junit.Assert.assertEquals(19 * 60, NudgeEngine.notifyAt(19 * 60, 22 * 60, 7 * 60))
    }
}
