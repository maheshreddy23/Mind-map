package com.maheshreddy.mym.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Plays mono float PCM through an AudioTrack, blocking (on IO) until the audio has been rendered. */
internal class AudioPlayer {
    @Volatile private var track: AudioTrack? = null

    suspend fun play(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, sampleRate / 5 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            var offset = 0
            val chunk = sampleRate / 10
            while (offset < samples.size) {
                coroutineContext.ensureActive()
                val n = t.write(samples, offset, minOf(chunk, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                if (n < 0) break
                offset += n
            }
            // Wait for the tail of the buffer to drain.
            val totalFrames = samples.size
            while (t.playState == AudioTrack.PLAYSTATE_PLAYING && t.playbackHeadPosition < totalFrames) {
                coroutineContext.ensureActive()
                Thread.sleep(20)
            }
        } finally {
            runCatching { t.stop() }
            t.release()
            track = null
        }
    }

    fun stop() {
        runCatching { track?.pause(); track?.flush() }
    }
}
