package com.freshernotes.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.freshernotes.core.audio.Wav
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/** Captures 16 kHz mono PCM from the microphone and emits float chunks in [-1, 1]. */
@Singleton
class AudioRecorder @Inject constructor(@ApplicationContext private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked in hasPermission()
    fun record(chunkSamples: Int = 1024): Flow<FloatArray> = flow {
        check(hasPermission()) { "Microphone permission not granted" }
        val rate = Wav.WHISPER_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, chunkSamples * 2 * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be opened" }
        val shorts = ShortArray(chunkSamples)
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                val n = record.read(shorts, 0, shorts.size)
                if (n < 0) error("Microphone read failed ($n)")
                if (n > 0) emit(Wav.shortsToFloats(shorts, n))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
