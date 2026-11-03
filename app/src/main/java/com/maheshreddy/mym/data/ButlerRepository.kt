package com.maheshreddy.mym.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maheshreddy.mym.core.llm.ButlerProfile
import com.maheshreddy.mym.core.llm.InterviewStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.butlerStore by preferencesDataStore(name = "butler")

/**
 * The persona profile and the butler's journal (requirements-v2 §4.3). Stored on the phone only. The journal is a
 * short running memory of what the butler learned about how the user files things, newest first.
 */
@Singleton
class ButlerRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val name = stringPreferencesKey("name")
        val callsYou = stringPreferencesKey("calls_you")
        val casual = floatPreferencesKey("casual")
        val warm = floatPreferencesKey("warm")
        val witty = floatPreferencesKey("witty")
        val detailed = floatPreferencesKey("detailed")
        val style = stringPreferencesKey("style")
        val quirks = stringPreferencesKey("quirks")
        val journal = stringPreferencesKey("journal")
        val nudgeSnoozedUntil = longPreferencesKey("nudge_snoozed_until")
    }

    val profile: Flow<ButlerProfile> = context.butlerStore.data.map { it.toProfile() }
    val journal: Flow<List<String>> = context.butlerStore.data.map { p -> p[Keys.journal].orEmpty().lines().filter { it.isNotBlank() } }
    val nudgeSnoozedUntil: Flow<Long> = context.butlerStore.data.map { it[Keys.nudgeSnoozedUntil] ?: 0L }

    private fun Preferences.toProfile(): ButlerProfile {
        val d = ButlerProfile()
        return ButlerProfile(
            name = this[Keys.name] ?: d.name,
            callsYou = this[Keys.callsYou] ?: d.callsYou,
            casual = this[Keys.casual] ?: d.casual,
            warm = this[Keys.warm] ?: d.warm,
            witty = this[Keys.witty] ?: d.witty,
            detailed = this[Keys.detailed] ?: d.detailed,
            style = InterviewStyle.parse(this[Keys.style]),
            quirks = this[Keys.quirks] ?: d.quirks,
        )
    }

    suspend fun save(p: ButlerProfile) {
        context.butlerStore.edit {
            it[Keys.name] = p.name.trim().take(24).ifEmpty { ButlerProfile.DEFAULT_NAME }
            it[Keys.callsYou] = p.callsYou.trim().take(40)
            it[Keys.casual] = p.casual.coerceIn(0f, 1f)
            it[Keys.warm] = p.warm.coerceIn(0f, 1f)
            it[Keys.witty] = p.witty.coerceIn(0f, 1f)
            it[Keys.detailed] = p.detailed.coerceIn(0f, 1f)
            it[Keys.style] = p.style.name
            it[Keys.quirks] = p.quirks.trim().take(300)
        }
    }

    /** Adds a learned note (newest first, de-duplicated, at most [JOURNAL_MAX] kept). */
    suspend fun remember(note: String) {
        val line = note.replace('\n', ' ').trim()
        if (line.isEmpty()) return
        context.butlerStore.edit { prefs ->
            val old = prefs[Keys.journal].orEmpty().lines().filter { it.isNotBlank() && it != line }
            prefs[Keys.journal] = (listOf(line) + old).take(JOURNAL_MAX).joinToString("\n")
        }
    }

    suspend fun clearJournal() {
        context.butlerStore.edit { it.remove(Keys.journal) }
    }

    suspend fun snoozeNudge(until: Long) {
        context.butlerStore.edit { it[Keys.nudgeSnoozedUntil] = until }
    }

    companion object {
        const val JOURNAL_MAX = 30
    }
}
