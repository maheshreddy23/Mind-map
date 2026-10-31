package com.freshernotes.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessions: SessionRepository,
    topics: TopicRepository,
) : ViewModel() {
    val topics = topics.topics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = sessions.recentSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startSession(onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val title = "Session " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
            onStarted(sessions.startSession(title))
        }
    }
}
