package com.maheshreddy.mym.tts

import java.io.Closeable

/** Speaks text aloud. [speak] suspends until playback finishes; cancelling it stops playback. */
interface SpeechSynthesizer : Closeable {
    val name: String
    suspend fun speak(text: String, rate: Float = 1.0f)
    fun stop()

    /** Pitch (1.0 = normal) and a specific voice; only Android TTS supports these, Piper voices are fixed. */
    fun configure(pitch: Float, voice: String?) = Unit
}
