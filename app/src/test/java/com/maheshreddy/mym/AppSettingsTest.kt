package com.maheshreddy.mym

import com.maheshreddy.mym.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsMatchPersonaLimits() {
        val s = AppSettings()
        assertTrue("persona requires replies under 40 words", s.maxReplyWords <= 40)
        assertEquals(1, s.questionEveryNTurns)
    }

    @Test
    fun newEntriesArePrivateByDefault() = assertEquals(com.maheshreddy.mym.core.library.Privacy.PRIVATE, AppSettings().defaultPrivacy)
}
