package com.maheshreddy.mym.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.audio.ListenState
import com.maheshreddy.mym.audio.SpeechInput
import com.maheshreddy.mym.data.ButlerRepository
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.engine.LlamaLanguageModel
import com.maheshreddy.mym.engine.MissingModelException
import com.maheshreddy.mym.search.DocKind
import com.maheshreddy.mym.search.SearchIndex
import com.maheshreddy.mym.core.library.AskPrompt
import com.maheshreddy.mym.core.library.AskSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Ask the library (v2 §7): keyword retrieval (FTS5) over entries → the LLM answers from the numbered entries only
 * → citations become chips that open the entry. Nothing found → offer an interview. All on the phone.
 */
@HiltViewModel
class AskViewModel @Inject constructor(
    private val index: SearchIndex,
    private val library: LibraryRepository,
    private val engines: Engines,
    private val butler: ButlerRepository,
    private val speech: SpeechInput,
) : ViewModel() {
    private val exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    private val mutex = Mutex()
    private var nextId = 0
    private var asked: String? = null

    val state: StateFlow<AskUiState> = combine(exchanges, speech.state) { ex, listen ->
        AskUiState(ex, listening = listen is ListenState.Listening)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState())

    init {
        // "Ask by voice": push-to-talk through whisper, then ask the transcript.
        viewModelScope.launch { speech.transcripts.collect { ask(it.text) } }
    }

    /** Asks [question] once per screen (used for "Ask about this" from a topic). */
    fun askOnce(question: String) {
        if (asked == question) return
        asked = question
        ask(question)
    }

    fun toggleVoice() {
        if (speech.isRecording()) speech.stop() else speech.start(viewModelScope, continuous = false)
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val id = nextId++
        exchanges.update { it + Exchange(id, q, Reply.Thinking("Looking through your entries…")) }
        viewModelScope.launch { mutex.withLock { answer(id, q) } }
    }

    private fun set(id: Int, reply: Reply) = exchanges.update { list -> list.map { if (it.id == id) it.copy(reply = reply) else it } }

    private suspend fun answer(id: Int, q: String) {
        try {
            val ids = index.searchAny(AskPrompt.keywords(q), DocKind.ENTRY, AskPrompt.MAX_SOURCES)
            val sources = ids.mapNotNull { eid ->
                library.entry(eid)?.let { e -> AskSource(e.id, e.title, e.body, e.topicId?.let { library.topic(it)?.title }) }
            }
            if (sources.isEmpty()) return set(id, Reply.Nothing)
            set(id, Reply.Thinking("Reading ${sources.size} ${if (sources.size == 1) "entry" else "entries"}…"))
            val profile = butler.profile.first()
            val model = LlamaLanguageModel(engines.llm())
            val raw = StringBuilder()
            model.stream(AskPrompt.messages(profile.displayName, q, sources), maxTokens = 220, temperature = 0.2f).collect { raw.append(it) }
            val text = raw.toString().trim()
            if (AskPrompt.isNothing(text)) return set(id, Reply.Nothing)
            val parts = AskPrompt.parse(text, sources)
            set(id, Reply.Answer(parts, AskPrompt.cited(parts).size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingModelException) {
            set(id, Reply.Failed("${e.kind.label} is not installed. Add it in Settings."))
        } catch (e: Exception) {
            set(id, Reply.Failed(e.message ?: "Something went wrong"))
        }
    }

    override fun onCleared() {
        speech.stop()
    }
}
