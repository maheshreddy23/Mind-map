package com.maheshreddy.mym.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.maheshreddy.mym.core.library.Privacy
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Max words per fresher reply (persona says < 40). */
    val maxReplyWords: Int = 40,
    /** Ask a question after every N senior turns (1 = after every turn). */
    val questionEveryNTurns: Int = 1,
    /** Use VAD-driven continuous listening instead of push-to-talk. */
    val continuousMode: Boolean = false,
    /** Voice identifier: "piper" or "system". */
    val voice: String = "piper",
    /** Speech rate multiplier for TTS. */
    val speechRate: Float = 1.0f,
    /** Privacy level given to new entries (v2 decision 4: Private). */
    val defaultPrivacy: Privacy = Privacy.DEFAULT,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val maxReplyWords = intPreferencesKey("max_reply_words")
        val questionEvery = intPreferencesKey("question_every")
        val continuous = booleanPreferencesKey("continuous")
        val voice = stringPreferencesKey("voice")
        val speechRate = intPreferencesKey("speech_rate_pct")
        val defaultPrivacy = stringPreferencesKey("default_privacy")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        maxReplyWords = this[Keys.maxReplyWords] ?: 40,
        questionEveryNTurns = this[Keys.questionEvery] ?: 1,
        continuousMode = this[Keys.continuous] ?: false,
        voice = this[Keys.voice] ?: "piper",
        speechRate = (this[Keys.speechRate] ?: 100) / 100f,
        defaultPrivacy = Privacy.parse(this[Keys.defaultPrivacy]) ?: Privacy.DEFAULT,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.maxReplyWords] = next.maxReplyWords.coerceIn(10, 80)
            prefs[Keys.questionEvery] = next.questionEveryNTurns.coerceIn(1, 5)
            prefs[Keys.continuous] = next.continuousMode
            prefs[Keys.voice] = next.voice
            prefs[Keys.speechRate] = (next.speechRate * 100).toInt().coerceIn(50, 200)
            prefs[Keys.defaultPrivacy] = next.defaultPrivacy.name
        }
    }
}
