package com.maheshreddy.mym.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Fallback: the platform TextToSpeech engine (works offline when the device has an offline voice installed). */
class SystemSynthesizer(context: Context) : SpeechSynthesizer {
    override val name = "Android TTS"
    private val ready = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    @Volatile private var pitch = 1.0f
    @Volatile private var voiceName: String? = null

    override fun configure(pitch: Float, voice: String?) {
        this.pitch = pitch
        this.voiceName = voice?.takeIf { it.isNotBlank() }
    }

    /** Offline English voices installed on the phone (names for Settings → Voice). */
    suspend fun offlineVoices(): List<String> {
        if (!ready.await()) return emptyList()
        return runCatching {
            tts.voices.orEmpty().filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }.map { it.name }.sorted()
        }.getOrDefault(emptyList())
    }

    override suspend fun speak(text: String, rate: Float) {
        check(ready.await()) { "Android TextToSpeech is unavailable" }
        tts.setLanguage(Locale.US)
        voiceName?.let { name -> tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it } }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
            })
            cont.invokeOnCancellation { tts.stop() }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
        }
    }

    override fun stop() {
        tts.stop()
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }
}
