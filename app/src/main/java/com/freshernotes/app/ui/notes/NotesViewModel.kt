package com.freshernotes.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val topics: TopicRepository,
) : ViewModel() {
    private val topicId: Long = checkNotNull(savedState["topicId"])
    val topic = topics.observe(topicId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(markdown: String) {
        viewModelScope.launch { topics.saveMarkdown(topicId, markdown) }
    }
}
