package com.maheshreddy.mym.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.audio.ListenState
import com.maheshreddy.mym.audio.SpeechInput
import com.maheshreddy.mym.core.library.AnswerPart
import com.maheshreddy.mym.core.library.AskPrompt
import com.maheshreddy.mym.core.library.AskSource
import com.maheshreddy.mym.core.library.Candidate
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.core.library.Retrieval
import com.maheshreddy.mym.data.ButlerRepository
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.db.VectorDao
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.engine.LlamaLanguageModel
import com.maheshreddy.mym.engine.MissingModelException
import com.maheshreddy.mym.models.ModelStore
import com.maheshreddy.mym.search.DocKind
import com.maheshreddy.mym.search.EmbeddingIndexer
import com.maheshreddy.mym.search.SearchIndex
import com.maheshreddy.mym.search.embeddingKey
import com.maheshreddy.mym.tts.SpeechSynthesizer
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
 * Ask the library (v2 §7), all on the phone: the question is matched to entries by meaning (sentence embeddings)
 * and by words (FTS5), fused; entries outside the privacy filter are never shown to the model. The active LLM
 * answers only from those numbered entries, and the citations become chips that open the entry. Nothing
 * relevant → it says so and offers an interview on the subject.
 */
@HiltViewModel
class AskViewModel @Inject constructor(
    private val index: SearchIndex,
    private val library: LibraryRepository,
    private val vectors: VectorDao,
    private val models: ModelStore,
    private val engines: Engines,
    private val butler: ButlerRepository,
    private val settings: SettingsRepository,
    private val speech: SpeechInput,
    embeddings: EmbeddingIndexer,
) : ViewModel() {
    private val exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    private val include = MutableStateFlow(Privacy.entries.toSet())
    private val mutex = Mutex()
    private var nextId = 0
    private var asked: String? = null
    private var voice: SpeechSynthesizer? = null

    private val suggestions = combine(library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts()) { topics, subjects, counts ->
        val updated = topics.associate { it.id to it.updatedAt }
        val tiles = com.maheshreddy.mym.ui.library.LibraryUi.tiles(topics, subjects, counts)
            .filter { it.entries > 0 }.sortedByDescending { updated[it.id] ?: 0L }
        Retrieval.suggestions(tiles.map { it.title to it.openQuestions }, tiles.mapNotNull { it.subject }.distinct()) to tiles.isNotEmpty()
    }

    val state: StateFlow<AskUiState> = combine(exchanges, speech.state, include, suggestions, settings.settings) { ex, listen, inc, (sugg, hasLibrary), prefs ->
        AskUiState(ex, listening = listen is ListenState.Listening, include = inc, suggestions = sugg, libraryEmpty = !hasLibrary, speakAnswers = prefs.speakAnswers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState())

    init {
        // "Ask by voice": push-to-talk through whisper, then ask the transcript.
        viewModelScope.launch { speech.transcripts.collect { ask(it.text) } }
        // Catch up on any entries not embedded yet (e.g. right after an embedding model was installed).
        embeddings.request()
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

    fun togglePrivacy(p: Privacy) = include.update { if (p in it && it.size > 1) it - p else it + p }

    fun toggleSpeak() = viewModelScope.launch {
        val on = !settings.settings.first().speakAnswers
        settings.update { it.copy(speakAnswers = on) }
        if (!on) voice?.stop()
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val id = nextId++
        exchanges.update { it + Exchange(id, q, Reply.Thinking("Looking through your entries…")) }
        viewModelScope.launch { mutex.withLock { answer(id, q) } }
    }

    private fun set(id: Int, reply: Reply) = exchanges.update { list -> list.map { if (it.id == id) it.copy(reply = reply) else it } }

    private suspend fun retrieve(q: String): List<AskSource> {
        val keywordIds = index.searchAny(AskPrompt.keywords(q), DocKind.ENTRY, 20)
        val key = models.embeddingKey()
        val query = key?.let { runCatching { engines.embedder()?.embed(q) }.getOrNull() }
        val stored = if (query != null) vectors.allFor(key).associate { it.entryId to Retrieval.fromBytes(it.vector) } else emptyMap()
        val entries = library.allFiledEntries().associateBy { it.id }
        val candidates = entries.values.map { Candidate(it.id, Privacy.parse(it.privacy) ?: Privacy.DEFAULT, stored[it.id]) }
        val hits = Retrieval.rank(query, candidates, keywordIds, include.value, AskPrompt.MAX_SOURCES, Retrieval.minSimilarityFor(key))
        return hits.mapNotNull { h -> entries[h.entryId]?.let { e -> AskSource(e.id, e.title, e.body, e.topicId?.let { library.topic(it)?.title }) } }
    }

    private suspend fun answer(id: Int, q: String) {
        try {
            val sources = retrieve(q)
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
            speak(parts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingModelException) {
            set(id, Reply.Failed("${e.kind.label} is not installed. Add it in Settings → Models."))
        } catch (e: Exception) {
            set(id, Reply.Failed(e.message ?: "Something went wrong"))
        }
    }

    /** Reads the answer aloud (without the citation numbers) when "Speak answers" is on. */
    private suspend fun speak(parts: List<AnswerPart>) {
        val prefs = settings.settings.first()
        if (!prefs.speakAnswers) return
        val text = parts.filterIsInstance<AnswerPart.Text>().joinToString("") { it.text }.trim()
        if (text.isEmpty()) return
        runCatching {
            val synth = engines.synthesizer(preferPiper = prefs.voice == "piper").also { it.configure(prefs.pitch, prefs.systemVoice.ifBlank { null }) }
            voice = synth
            synth.speak(text, prefs.speechRate)
        }
    }

    override fun onCleared() {
        speech.stop()
        voice?.stop()
    }
}
