package com.freshernotes.app.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Live measurements against the performance targets (STT faster than real time, first LLM token < 2 s). */
data class Perf(
    val sttAudioSeconds: Double? = null,
    val sttProcessingSeconds: Double? = null,
    val llmFirstTokenMs: Long? = null,
    val llmTokensPerSecond: Double? = null,
) {
    /** Real-time factor: processing time / audio length. Below 1.0 means faster than real time. */
    val sttRealTimeFactor: Double? get() = if (sttAudioSeconds != null && sttProcessingSeconds != null && sttAudioSeconds > 0) sttProcessingSeconds / sttAudioSeconds else null
}

@Singleton
class PerfStats @Inject constructor() {
    private val _perf = MutableStateFlow(Perf())
    val perf: StateFlow<Perf> = _perf.asStateFlow()

    fun recordStt(audioSeconds: Double, processingSeconds: Double) {
        _perf.value = _perf.value.copy(sttAudioSeconds = audioSeconds, sttProcessingSeconds = processingSeconds)
    }

    fun recordLlm(firstTokenMs: Long, tokens: Int, totalMs: Long) {
        val genMs = (totalMs - firstTokenMs).coerceAtLeast(1)
        _perf.value = _perf.value.copy(llmFirstTokenMs = firstTokenMs, llmTokensPerSecond = if (tokens > 1) (tokens - 1) * 1000.0 / genMs else null)
    }
}
