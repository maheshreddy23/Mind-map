package com.freshernotes.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoded PCM audio as mono float samples in [-1, 1]. */
class PcmAudio(val samples: FloatArray, val sampleRate: Int) {
    val durationSeconds: Double get() = samples.size.toDouble() / sampleRate
}

object Wav {
    const val WHISPER_SAMPLE_RATE = 16_000

    /** Parses a RIFF/WAVE file with 16-bit or 32-bit-float PCM, any channel count. Channels are averaged to mono. */
    fun decode(bytes: ByteArray): PcmAudio {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && tag(buf, 0) == "RIFF" && tag(buf, 8) == "WAVE") { "Not a WAV file" }
        var pos = 12
        var format = -1
        var channels = 0
        var rate = 0
        var bits = 0
        var data: Pair<Int, Int>? = null
        while (pos + 8 <= bytes.size) {
            val id = tag(buf, pos)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    format = buf.getShort(body).toInt() and 0xffff
                    channels = buf.getShort(body + 2).toInt()
                    rate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                    if (format == 0xFFFE && size >= 26) format = buf.getShort(body + 24).toInt() and 0xffff
                }
                "data" -> data = body to minOf(size, bytes.size - body)
            }
            pos = body + size + (size and 1)
        }
        val (start, len) = requireNotNull(data) { "WAV has no data chunk" }
        require(channels > 0 && rate > 0) { "WAV has no fmt chunk" }
        val bytesPerSample = bits / 8
        val frames = len / (bytesPerSample * channels)
        val out = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0f
            for (c in 0 until channels) {
                val at = start + (f * channels + c) * bytesPerSample
                acc += when {
                    format == 1 && bits == 16 -> buf.getShort(at) / 32768f
                    format == 3 && bits == 32 -> buf.getFloat(at)
                    else -> throw IllegalArgumentException("Unsupported WAV encoding: format=$format bits=$bits")
                }
            }
            out[f] = acc / channels
        }
        return PcmAudio(out, rate)
    }

    /** Encodes mono float samples as a 16-bit PCM WAV. */
    fun encode16(samples: FloatArray, sampleRate: Int): ByteArray {
        val dataLen = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataLen)
        samples.forEach { buf.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
        return buf.array()
    }

    /** Linear-interpolation resampler; adequate for speech going into whisper. */
    fun resample(audio: PcmAudio, targetRate: Int = WHISPER_SAMPLE_RATE): PcmAudio {
        if (audio.sampleRate == targetRate) return audio
        val ratio = audio.sampleRate.toDouble() / targetRate
        val n = (audio.samples.size / ratio).toInt()
        val src = audio.samples
        val out = FloatArray(n) { i ->
            val x = i * ratio
            val i0 = x.toInt().coerceAtMost(src.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(src.lastIndex)
            val t = (x - i0).toFloat()
            src[i0] * (1 - t) + src[i1] * t
        }
        return PcmAudio(out, targetRate)
    }

    /** Convenience: any supported WAV to 16 kHz mono floats for whisper. */
    fun toWhisperInput(bytes: ByteArray): FloatArray = resample(decode(bytes)).samples

    /** Converts 16-bit PCM shorts (as delivered by AudioRecord) to floats. */
    fun shortsToFloats(pcm: ShortArray, count: Int = pcm.size): FloatArray = FloatArray(count) { pcm[it] / 32768f }

    private fun tag(buf: ByteBuffer, at: Int): String =
        String(ByteArray(4) { buf.get(at + it) }, Charsets.US_ASCII)

}
