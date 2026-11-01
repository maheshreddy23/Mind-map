package com.freshernotes.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.settings.AppSettings
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.app.device.PerfStats
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.LlamaLanguageModel
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.Persona
import com.freshernotes.core.llm.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val engines: Engines,
    private val perfStats: PerfStats,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val perf = perfStats.perf

    private val _benchmark = MutableStateFlow<String?>(null)
    val benchmark = _benchmark.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    /** Measures time to first token for a realistic persona prompt (cold cache, as at the start of a session). */
    fun runLlmBenchmark() {
        viewModelScope.launch {
            _benchmark.value = "Loading model…"
            runCatching {
                val engine = engines.llm()
                val model = LlamaLanguageModel(engine)
                val messages = listOf(
                    ChatMessage(Role.SYSTEM, Persona.systemPrompt()),
                    ChatMessage(Role.USER, "In final assembly we use drill jigs located on tooling holes so every hole lands in the same place."),
                )
                engine.abort()
                _benchmark.value = "Generating…"
                val t0 = System.nanoTime()
                var first = -1L
                var chunks = 0
                model.stream(messages, maxTokens = 48, temperature = 0.7f).collect {
                    if (first < 0) first = (System.nanoTime() - t0) / 1_000_000
                    chunks++
                }
                perfStats.recordLlm(first, chunks, (System.nanoTime() - t0) / 1_000_000)
                "First token in $first ms (target < 2000 ms)"
            }.onSuccess { _benchmark.value = it }.onFailure { _benchmark.value = "Benchmark failed: ${it.message}" }
        }
    }
}
