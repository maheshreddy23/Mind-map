package com.maheshreddy.mym.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Generated speech: mono float PCM. */
class SynthesizedAudio(val samples: FloatArray, val sampleRate: Int) {
    val durationSeconds: Double get() = samples.size.toDouble() / sampleRate
}

/**
 * Piper (VITS) voice running on sherpa-onnx. [voiceDir] must hold the voice's .onnx file, tokens.txt and the
 * espeak-ng-data/ folder, as in the sherpa-onnx "vits-piper-*" voice packages.
 */
class PiperSynthesizer private constructor(private val tts: OfflineTts) : SpeechSynthesizer {
    override val name = "Piper"
    private val player = AudioPlayer()

    val sampleRate: Int get() = tts.sampleRate()

    suspend fun synthesize(text: String, rate: Float = 1.0f): SynthesizedAudio = withContext(Dispatchers.Default) {
        val audio = synchronized(tts) { tts.generate(text = text, sid = 0, speed = rate) }
        SynthesizedAudio(audio.samples, audio.sampleRate)
    }

    override suspend fun speak(text: String, rate: Float) {
        // Synthesize sentence by sentence so playback starts after the first sentence, not the whole reply.
        for (sentence in splitSentences(text)) {
            val audio = synthesize(sentence, rate)
            player.play(audio.samples, audio.sampleRate)
        }
    }

    override fun stop() = player.stop()

    override fun close() {
        stop()
        tts.release()
    }

    companion object {
        data class VoiceFiles(val model: File, val tokens: File, val espeakData: File)

        /** Finds the model files inside an imported voice folder (possibly nested one level). */
        fun locate(voiceDir: File): VoiceFiles? {
            val candidates = listOf(voiceDir) + (voiceDir.listFiles()?.filter { it.isDirectory } ?: emptyList())
            for (dir in candidates) {
                val model = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".onnx") } ?: continue
                val tokens = File(dir, "tokens.txt").takeIf { it.isFile } ?: continue
                val espeak = File(dir, "espeak-ng-data").takeIf { it.isDirectory } ?: continue
                return VoiceFiles(model, tokens, espeak)
            }
            return null
        }

        fun load(voiceDir: File, threads: Int = 2): PiperSynthesizer {
            val files = requireNotNull(locate(voiceDir)) {
                "Piper voice folder must contain a .onnx model, tokens.txt and espeak-ng-data/"
            }
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = files.model.absolutePath,
                        tokens = files.tokens.absolutePath,
                        dataDir = files.espeakData.absolutePath,
                    ),
                    numThreads = threads,
                    provider = "cpu",
                ),
            )
            return PiperSynthesizer(OfflineTts(assetManager = null, config = config))
        }

        internal fun splitSentences(text: String): List<String> =
            text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
