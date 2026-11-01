package com.freshernotes.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.models.ModelStore
import com.freshernotes.app.search.SearchHit
import com.freshernotes.app.search.SearchIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessions: SessionRepository,
    library: LibraryRepository,
    models: ModelStore,
    private val index: SearchIndex,
) : ViewModel() {
    val topics = library.observeAllTopics().map { it.take(8) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = sessions.recentSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inboxCount = library.observeInboxCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val missingModels = models.installed.map { installed -> com.freshernotes.app.models.ModelKind.entries.filter { it.required && it !in installed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    val results = _query.debounce(200).mapLatest { q -> if (q.isBlank()) emptyList() else runCatching { index.search(q) }.getOrDefault(emptyList<SearchHit>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun startSession(onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val title = "Session " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
            onStarted(sessions.startSession(title))
        }
    }
}
