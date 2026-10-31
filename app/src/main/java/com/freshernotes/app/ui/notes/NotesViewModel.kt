package com.freshernotes.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.TopicRepository
import com.freshernotes.app.notes.MarkdownExporter
import com.freshernotes.app.search.DocKind
import com.freshernotes.app.search.SearchIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val topics: TopicRepository,
    sessions: SessionRepository,
    private val index: SearchIndex,
    private val exporter: MarkdownExporter,
) : ViewModel() {
    private val topicId: Long = checkNotNull(savedState["topicId"])
    val topic = topics.observe(topicId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sessions = sessions.sessionsForTopic(topicId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun save(markdown: String) {
        viewModelScope.launch {
            topics.saveMarkdown(topicId, markdown)
            val t = topics.get(topicId) ?: return@launch
            index.upsert(DocKind.TOPIC, t.id, t.title, markdown)
            runCatching { exporter.export(t.slug, markdown) }
        }
    }

    fun export() {
        viewModelScope.launch {
            val t = topics.get(topicId) ?: return@launch
            _message.value = runCatching { exporter.export(t.slug, t.markdown) }
                .fold({ "Exported to Documents/FresherNotes/${t.slug}.md" }, { "Export failed: ${it.message}" })
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
