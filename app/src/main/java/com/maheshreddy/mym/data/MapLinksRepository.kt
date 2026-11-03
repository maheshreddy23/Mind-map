package com.maheshreddy.mym.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mapStore by preferencesDataStore(name = "map")

/** Topic links the user confirmed or dismissed on the Map. Pairs are stored as "a-b" with a < b. */
data class LinkDecisions(val confirmed: Set<Pair<Long, Long>> = emptySet(), val dismissed: Set<Pair<Long, Long>> = emptySet())

@Singleton
class MapLinksRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val confirmedKey = stringSetPreferencesKey("confirmed")
    private val dismissedKey = stringSetPreferencesKey("dismissed")

    val decisions: Flow<LinkDecisions> = context.mapStore.data.map { p ->
        LinkDecisions(parse(p[confirmedKey].orEmpty()), parse(p[dismissedKey].orEmpty()))
    }

    suspend fun confirm(a: Long, b: Long) = set(a, b, add = confirmedKey, remove = dismissedKey)
    suspend fun dismiss(a: Long, b: Long) = set(a, b, add = dismissedKey, remove = confirmedKey)

    private suspend fun set(a: Long, b: Long, add: androidx.datastore.preferences.core.Preferences.Key<Set<String>>, remove: androidx.datastore.preferences.core.Preferences.Key<Set<String>>) {
        val k = "${minOf(a, b)}-${maxOf(a, b)}"
        context.mapStore.edit { p ->
            p[add] = p[add].orEmpty() + k
            p[remove] = p[remove].orEmpty() - k
        }
    }

    private fun parse(s: Set<String>) = s.mapNotNull { v ->
        val parts = v.split('-')
        val a = parts.getOrNull(0)?.toLongOrNull(); val b = parts.getOrNull(1)?.toLongOrNull()
        if (a != null && b != null) a to b else null
    }.toSet()
}
