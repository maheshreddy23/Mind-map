package com.freshernotes.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptFilterTest {
    @Test fun blankAudioIsNotMeaningful() = assertFalse(TranscriptFilter.isMeaningful(" [BLANK_AUDIO] "))
    @Test fun hallucinatedThanksIsNotMeaningful() = assertFalse(TranscriptFilter.isMeaningful("Thank you."))
    @Test fun realSpeechIsMeaningful() = assertTrue(TranscriptFilter.isMeaningful("We drill through the template."))
    @Test fun cleanStripsTags() = assertEquals("We use NX.", TranscriptFilter.clean("(music) We use  NX. [BLANK_AUDIO]"))
}
