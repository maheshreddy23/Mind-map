package com.maheshreddy.mym.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.core.library.Retrieval
import com.maheshreddy.mym.core.map.KnowledgeMap
import com.maheshreddy.mym.core.map.MapEntryIn
import com.maheshreddy.mym.core.map.MapHit
import com.maheshreddy.mym.core.map.MapLink
import com.maheshreddy.mym.core.map.MapPeriod
import com.maheshreddy.mym.core.map.MapSubject
import com.maheshreddy.mym.core.map.MapTopicIn
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.MapLinksRepository
import com.maheshreddy.mym.data.db.VectorDao
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.models.ModelStore
import com.maheshreddy.mym.search.embeddingKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Map tab: builds the knowledge map on the phone from the library, the entry embeddings and the link decisions. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val vectors: VectorDao,
    private val models: ModelStore,
    private val links: MapLinksRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(MapFilters())
    private val selected = MutableStateFlow<MapHit?>(null)

    private val source = combine(
        library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts(), links.decisions, settings.settings,
    ) { topics, subjects, _, decisions, prefs -> Source(topics, subjects, decisions, prefs.starterSubjects) }

    private val layout = combine(source, filters) { s, f -> s to f }.mapLatest { (s, f) ->
        val key = models.embeddingKey()
        val vecs = if (key != null) vectors.allFor(key).associate { it.entryId to Retrieval.fromBytes(it.vector) } else emptyMap()
        val entries = library.allFiledEntries().mapNotNull { e ->
            val t = e.topicId ?: return@mapNotNull null
            MapEntryIn(t, e.createdAt, Privacy.parse(e.privacy) ?: Privacy.DEFAULT, EntryType.entries.firstOrNull { it.name == e.type } ?: EntryType.KNOWLEDGE, e.title + " " + e.body, vecs[e.id])
        }
        KnowledgeMap.build(
            subjects = s.subjects.map { MapSubject(it.id, it.title) },
            topics = s.topics.map { MapTopicIn(it.id, it.subjectId, it.title, it.summary) },
            entries = entries,
            confirmed = s.decisions.confirmed, dismissed = s.decisions.dismissed,
            privacy = f.privacy,
            since = f.period.days?.let { System.currentTimeMillis() - it * 86_400_000L },
            starters = s.starters,
        )
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<MapUiState> = combine(layout, filters, selected) { l, f, sel ->
        // Keep the selection in step with the rebuilt map (or drop it if the item is gone).
        val fresh = when (sel) {
            is MapHit.Topic -> l.nodes.firstOrNull { it.id == sel.node.id }?.let { MapHit.Topic(it) }
            is MapHit.Subject -> l.clusters.firstOrNull { it.subject == sel.cluster.subject }?.let { MapHit.Subject(it) }
            is MapHit.Link -> l.links.firstOrNull { it.a == sel.link.a && it.b == sel.link.b }?.let { MapHit.Link(it) }
            null -> null
        }
        MapUiState(l, f.privacy, f.period, f.growth, fresh, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun togglePrivacy(p: Privacy) = filters.update { f -> f.copy(privacy = if (p in f.privacy && f.privacy.size > 1) f.privacy - p else f.privacy + p) }
    fun period(p: MapPeriod) = filters.update { it.copy(period = p) }
    fun toggleGrowth() = filters.update { it.copy(growth = !it.growth) }
    fun select(hit: MapHit?) { selected.value = hit }
    fun confirm(link: MapLink) = viewModelScope.launch { links.confirm(link.a, link.b) }
    fun dismiss(link: MapLink) = viewModelScope.launch { links.dismiss(link.a, link.b); selected.value = null }

    private data class Source(
        val topics: List<com.maheshreddy.mym.data.db.TopicEntity>,
        val subjects: List<com.maheshreddy.mym.data.db.SubjectEntity>,
        val decisions: com.maheshreddy.mym.data.LinkDecisions,
        val starters: List<String>,
    )
}

data class MapFilters(val privacy: Set<Privacy> = Privacy.entries.toSet(), val period: MapPeriod = MapPeriod.ALL, val growth: Boolean = false)
