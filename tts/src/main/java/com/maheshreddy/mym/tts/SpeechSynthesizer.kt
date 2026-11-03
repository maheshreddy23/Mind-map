package com.maheshreddy.mym.tts

import java.io.Closeable

/** Speaks text aloud. [speak] suspends until playback finishes; cancelling it stops playback. */
interface SpeechSynthesizer : Closeable {
    val name: String
    suspend fun speak(text: String, rate: Float = 1.0f)
    fun stop()
}
