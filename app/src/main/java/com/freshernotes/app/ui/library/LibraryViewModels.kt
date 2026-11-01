package com.freshernotes.app.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.Pick
import com.freshernotes.app.data.db.EntryEntity
import com.freshernotes.app.library.TopicSummarizer
import com.freshernotes.app.notes.MarkdownExporter
import com.freshernotes.core.library.EntryType
import com.freshernotes.core.library.Privacy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import com.freshernotes.app.search.SearchIndex
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared plumbing: one-line messages for a snackbar, and errors from the repository shown instead of crashing. */
abstract class LibraryBaseViewModel(protected val library: LibraryRepository) : ViewModel() {
    val subjects = library.observeSubjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allTopics = library.observeAllTopics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }
    protected fun say(text: String) { _message.value = text }

    protected fun act(success: String? = null, then: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let(::say)
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(e.message ?: "Something went wrong")
            }
        }
    }
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    savedState: SavedStateHandle,
    library: LibraryRepository,
    sessions: com.freshernotes.app.data.SessionRepository,
) : LibraryBaseViewModel(library) {
    /** Set when opened from wrap-up: the screen shows that session's results first. */
    val sessionId: Long? = savedState.get<Long>("sessionId")?.takeIf { it > 0 }

    private val justFiled = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    private val sessionEntries = sessionId?.let { library.observeSessionEntries(it) } ?: flowOf(emptyList())
    private val sessionStatus = sessionId?.let { id -> sessions.observeSession(id) } ?: flowOf(null)

    private val names = combine(subjects, allTopics) { s, t -> s to t }

    val state = combine(
        combine(library.observeInbox(), library.observeFiledToday(), sessionEntries) { a, b, c -> Triple(a, b, c) },
        sessionStatus, names, justFiled,
    ) { (inbox, today, mine), session, (subjects, topics), confirmed ->
        fun subjectName(id: Long?) = subjects.firstOrNull { it.id == id }?.title
        fun topic(id: Long?) = topics.firstOrNull { it.id == id }
        val ordered = if (sessionId != null) inbox.sortedBy { if (it.sessionId == sessionId) 0 else 1 } else inbox
        val needs = ordered.map { e ->
            val sp = e.proposedSubjectPick(); val tp = e.proposedTopicPick()
            InboxItem(
                entry = e,
                subject = (sp as? Pick.Existing)?.let { subjectName(it.id) } ?: (sp as? Pick.New)?.name,
                topic = (tp as? Pick.Existing)?.let { topic(it.id)?.title } ?: (tp as? Pick.New)?.name,
                newSubject = sp is Pick.New, newTopic = tp is Pick.New, canAccept = sp != null && tp != null,
            )
        }
        val filedList = (if (sessionId != null) mine.filter { it.status == "FILED" } else today).map { e ->
            val t = topic(e.topicId)
            FiledItem(e, listOfNotNull(subjectName(t?.subjectId), t?.title).joinToString(" › "), undoable = e.autoFiled)
        }
        InboxUiState(
            sessionMode = sessionId != null,
            filing = session?.notesStatus == "PENDING",
            failed = session?.notesStatus == "FAILED",
            filedFromSession = mine.count { it.status == "FILED" },
            waitingFromSession = mine.count { it.status == "INBOX" },
            needs = needs, confirmations = confirmed, filed = filedList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    private fun confirm(e: EntryEntity, topicId: Long) {
        viewModelScope.launch {
            val t = library.topic(topicId)
            justFiled.value = justFiled.value + (e.id to "Filed \"${e.title}\" under ${t?.title ?: "its topic"}.")
        }
    }

    /** One-tap accept of the butler's proposal (only offered when both subject and topic are proposed). */
    fun accept(e: EntryEntity) {
        val s = e.proposedSubjectPick() ?: return
        val t = e.proposedTopicPick() ?: return
        act { confirm(e, library.accept(e.id, s, t)) }
    }

    fun file(e: EntryEntity, c: FilingChoice) = act { confirm(e, library.accept(e.id, c.subject, c.topic, c.type, c.privacy)) }
    fun reject(e: EntryEntity) = act("Discarded") { library.deleteEntry(e.id) }
    fun undo(e: EntryEntity) = act("Moved back to the Inbox") { library.undoFiling(e.id) }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryRepository,
    private val index: SearchIndex,
) : LibraryBaseViewModel(library) {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<Privacy?>(null)

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val results = query.debounce(200).mapLatest { q ->
        if (q.isBlank()) emptyList() else runCatching { index.search(q) }.getOrDefault(emptyList())
    }

    private val data = combine(library.observeSubjects(), library.observeAllTopics(), library.observeTopicCounts(), filter) { subjects, topics, counts, f ->
        val all = LibraryUi.tiles(topics, subjects, counts)
        val tiles = LibraryUi.tiles(topics, subjects, counts, f)
        val tileIds = tiles.associateBy { it.id }
        LibraryUiState(
            topicCount = topics.size,
            entryCount = all.sumOf { it.entries },
            filter = f,
            shelves = LibraryUi.shelves(subjects, topics, tiles, f),
            unassigned = topics.filter { it.subjectId == null }.mapNotNull { tileIds[it.id] },
        )
    }

    val state = combine(data, query, results) { d, q, r -> d.copy(query = q, results = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(p: Privacy?) { filter.value = p }
    fun createSubject(name: String) = act("Subject created") { library.createSubject(name) }
}

@HiltViewModel
class SubjectViewModel @Inject constructor(savedState: SavedStateHandle, library: LibraryRepository) : LibraryBaseViewModel(library) {
    val subjectId: Long = checkNotNull(savedState["subjectId"])
    val subject = library.observeSubject(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val topics = library.observeTopics(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tiles = combine(library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts()) { t, s, c -> LibraryUi.tiles(t, s, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) = act("Renamed") { library.renameSubject(subjectId, name) }
    fun mergeInto(targetId: Long, onDone: () -> Unit) = act("Merged", onDone) { library.mergeSubjects(subjectId, targetId) }
    fun delete(onDone: () -> Unit) = act("Subject deleted; its topics are under \"Needs a subject\"", onDone) { library.deleteSubject(subjectId) }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TopicViewModel @Inject constructor(
    savedState: SavedStateHandle,
    library: LibraryRepository,
    private val summarizer: TopicSummarizer,
    private val exporter: MarkdownExporter,
) : LibraryBaseViewModel(library) {
    val topicId: Long = checkNotNull(savedState["topicId"])
    val topic = library.observeTopic(topicId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val subject = topic.flatMapLatest { t -> t?.subjectId?.let { library.observeSubject(it) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val entries = library.observeTopicEntries(topicId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _summarizing = MutableStateFlow(false)
    val summarizing = _summarizing.asStateFlow()

    fun rename(name: String) = act("Renamed") { library.renameTopic(topicId, name) }
    fun moveTo(subject: Pick) = act("Moved") { library.moveTopic(topicId, subject) }
    fun mergeInto(targetId: Long, onDone: () -> Unit) = act("Merged", onDone) { library.mergeTopics(topicId, targetId) }
    fun delete(deleteEntries: Boolean, onDone: () -> Unit) =
        act(if (deleteEntries) "Topic and entries deleted" else "Topic deleted; its entries are in the Inbox", onDone) { library.deleteTopic(topicId, deleteEntries) }
    fun discardLegacy() = act("v1 page removed") { library.discardLegacyPage(topicId) }
    fun clearSummary() = act { library.setSummary(topicId, "") }

    fun rewriteSummary() {
        if (_summarizing.value) return
        _summarizing.value = true
        viewModelScope.launch {
            summarizer.rewrite(topicId).onFailure { say("Summary failed: ${it.message}") }
            _summarizing.value = false
        }
    }

    fun export() = act {
        val t = library.topic(topicId) ?: return@act
        say(runCatching { exporter.export(t.slug, t.markdown) }.fold({ "Exported to Documents/FresherNotes/${t.slug}.md" }, { "Export failed: ${it.message}" }))
    }
}

@HiltViewModel
class EntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    library: LibraryRepository,
) : LibraryBaseViewModel(library) {
    val entryId: Long = checkNotNull(savedState["entryId"])
    val entry = library.observeEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(title: String, body: String, type: EntryType, privacy: Privacy) = act("Saved") { library.updateEntry(entryId, title, body, type, privacy) }
    fun move(c: FilingChoice) = act("Moved") {
        val e = library.entry(entryId) ?: return@act
        if (e.status == "INBOX") library.accept(entryId, c.subject, c.topic, c.type, c.privacy) else {
            library.moveEntry(entryId, c.subject, c.topic)
            library.updateEntry(entryId, e.title, e.body, c.type, c.privacy)
        }
    }
    fun delete(onDone: () -> Unit) = act("Entry deleted", onDone) { library.deleteEntry(entryId) }
}
