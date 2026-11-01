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
class InboxViewModel @Inject constructor(library: LibraryRepository) : LibraryBaseViewModel(library) {
    val inbox = library.observeInbox().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val filedToday = library.observeFiledToday().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-tap accept of the butler's proposal (only offered when both subject and topic are proposed). */
    fun accept(e: EntryEntity) {
        val s = e.proposedSubjectPick() ?: return
        val t = e.proposedTopicPick() ?: return
        act("Filed") { library.accept(e.id, s, t) }
    }

    fun file(e: EntryEntity, c: FilingChoice) = act("Filed") { library.accept(e.id, c.subject, c.topic, c.type, c.privacy) }
    fun reject(e: EntryEntity) = act("Rejected") { library.deleteEntry(e.id) }
    fun undo(e: EntryEntity) = act("Moved back to the Inbox") { library.undoFiling(e.id) }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(library: LibraryRepository) : LibraryBaseViewModel(library) {
    val unassigned = library.observeUnassignedTopics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inboxCount = library.observeInboxCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun createSubject(name: String) = act("Subject created") { library.createSubject(name) }
}

@HiltViewModel
class SubjectViewModel @Inject constructor(savedState: SavedStateHandle, library: LibraryRepository) : LibraryBaseViewModel(library) {
    val subjectId: Long = checkNotNull(savedState["subjectId"])
    val subject = library.observeSubject(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val topics = library.observeTopics(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
