package com.maheshreddy.mym.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maheshreddy.mym.core.library.Privacy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Every user setting (Settings screen). Stored on the phone only. */
data class AppSettings(
    /** Max words per butler question. */
    val maxReplyWords: Int = 40,
    /** Ask a question after every N turns (1 = after every turn). */
    val questionEveryNTurns: Int = 1,
    /** Use VAD-driven continuous listening instead of push-to-talk. */
    val continuousMode: Boolean = false,
    /** Voice engine: "piper" or "system" (Android TTS). */
    val voice: String = "piper",
    /** Android TTS voice name; empty = system default. */
    val systemVoice: String = "",
    /** Speech rate multiplier for TTS. */
    val speechRate: Float = 1.0f,
    /** Pitch multiplier (Android TTS; Piper voices have a fixed pitch). */
    val pitch: Float = 1.0f,
    /** Speak Ask answers aloud. */
    val speakAnswers: Boolean = false,
    /** Privacy level given to new entries (decision 4: Private). */
    val defaultPrivacy: Privacy = Privacy.DEFAULT,
    /** Speech recognition: English-only or multilingual (auto-detect). */
    val multilingual: Boolean = false,
    /** Words passed to whisper as its initial prompt so jargon is spelled right. */
    val vocabulary: List<String> = DEFAULT_VOCABULARY,
    /** Areas the user wants to map (onboarding); shown as empty clusters on the Map until real subjects exist. */
    val starterSubjects: List<String> = DEFAULT_STARTERS,
    /** Download models on Wi-Fi only. */
    val wifiOnly: Boolean = true,
    /** Nudges. */
    val nudgeUnfinished: Boolean = true,
    val nudgeResurface: Boolean = true,
    val nudgeDaily: Boolean = true,
    /** Daily nudge time, minutes after midnight. */
    val nudgeTimeMin: Int = 19 * 60,
    /** Quiet hours (no notifications), minutes after midnight; may wrap past midnight. */
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    /** Ask for fingerprint/face or the screen-lock PIN when opening the app. */
    val appLock: Boolean = false,
    /** First-run onboarding finished. */
    val onboarded: Boolean = false,
) {
    companion object {
        /** requirements-v2 §5 examples of subjects. Suggestions only: real subjects are always confirmed first. */
        val DEFAULT_STARTERS = listOf("Jigs and Fixtures", "PLM", "Project Management", "Personal Finance", "Life")
        val DEFAULT_VOCABULARY = listOf("PLM", "CCPM", "RFP", "Teamcenter", "NX", "CATIA", "jig", "fixture", "datum", "BOM", "ECO")
    }
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val maxReplyWords = intPreferencesKey("max_reply_words")
        val questionEvery = intPreferencesKey("question_every")
        val continuous = booleanPreferencesKey("continuous")
        val voice = stringPreferencesKey("voice")
        val systemVoice = stringPreferencesKey("system_voice")
        val speechRate = intPreferencesKey("speech_rate_pct")
        val pitch = intPreferencesKey("pitch_pct")
        val speakAnswers = booleanPreferencesKey("speak_answers")
        val defaultPrivacy = stringPreferencesKey("default_privacy")
        val multilingual = booleanPreferencesKey("multilingual")
        val vocabulary = stringPreferencesKey("vocabulary")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val nudgeUnfinished = booleanPreferencesKey("nudge_unfinished")
        val nudgeResurface = booleanPreferencesKey("nudge_resurface")
        val nudgeDaily = booleanPreferencesKey("nudge_daily")
        val nudgeTime = intPreferencesKey("nudge_time")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val appLock = booleanPreferencesKey("app_lock")
        val onboarded = booleanPreferencesKey("onboarded")
        val starters = stringPreferencesKey("starter_subjects")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            maxReplyWords = this[Keys.maxReplyWords] ?: d.maxReplyWords,
            questionEveryNTurns = this[Keys.questionEvery] ?: d.questionEveryNTurns,
            continuousMode = this[Keys.continuous] ?: d.continuousMode,
            voice = this[Keys.voice] ?: d.voice,
            systemVoice = this[Keys.systemVoice] ?: d.systemVoice,
            speechRate = (this[Keys.speechRate] ?: 100) / 100f,
            pitch = (this[Keys.pitch] ?: 100) / 100f,
            speakAnswers = this[Keys.speakAnswers] ?: d.speakAnswers,
            defaultPrivacy = Privacy.parse(this[Keys.defaultPrivacy]) ?: Privacy.DEFAULT,
            multilingual = this[Keys.multilingual] ?: d.multilingual,
            vocabulary = this[Keys.vocabulary]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.vocabulary,
            wifiOnly = this[Keys.wifiOnly] ?: d.wifiOnly,
            nudgeUnfinished = this[Keys.nudgeUnfinished] ?: d.nudgeUnfinished,
            nudgeResurface = this[Keys.nudgeResurface] ?: d.nudgeResurface,
            nudgeDaily = this[Keys.nudgeDaily] ?: d.nudgeDaily,
            nudgeTimeMin = this[Keys.nudgeTime] ?: d.nudgeTimeMin,
            quietStartMin = this[Keys.quietStart] ?: d.quietStartMin,
            quietEndMin = this[Keys.quietEnd] ?: d.quietEndMin,
            appLock = this[Keys.appLock] ?: d.appLock,
            onboarded = this[Keys.onboarded] ?: d.onboarded,
            starterSubjects = this[Keys.starters]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.starterSubjects,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.maxReplyWords] = next.maxReplyWords.coerceIn(10, 80)
            prefs[Keys.questionEvery] = next.questionEveryNTurns.coerceIn(1, 5)
            prefs[Keys.continuous] = next.continuousMode
            prefs[Keys.voice] = next.voice
            prefs[Keys.systemVoice] = next.systemVoice
            prefs[Keys.speechRate] = (next.speechRate * 100).toInt().coerceIn(50, 200)
            prefs[Keys.pitch] = (next.pitch * 100).toInt().coerceIn(50, 200)
            prefs[Keys.speakAnswers] = next.speakAnswers
            prefs[Keys.defaultPrivacy] = next.defaultPrivacy.name
            prefs[Keys.multilingual] = next.multilingual
            prefs[Keys.vocabulary] = next.vocabulary.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
            prefs[Keys.wifiOnly] = next.wifiOnly
            prefs[Keys.starters] = next.starterSubjects.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
            prefs[Keys.nudgeUnfinished] = next.nudgeUnfinished
            prefs[Keys.nudgeResurface] = next.nudgeResurface
            prefs[Keys.nudgeDaily] = next.nudgeDaily
            prefs[Keys.nudgeTime] = next.nudgeTimeMin.coerceIn(0, 24 * 60 - 1)
            prefs[Keys.quietStart] = next.quietStartMin.coerceIn(0, 24 * 60 - 1)
            prefs[Keys.quietEnd] = next.quietEndMin.coerceIn(0, 24 * 60 - 1)
            prefs[Keys.appLock] = next.appLock
            prefs[Keys.onboarded] = next.onboarded
        }
    }

    companion object {
        /** Whisper's initial prompt from the vocabulary list. */
        fun whisperPrompt(vocabulary: List<String>) =
            if (vocabulary.isEmpty()) "" else "Engineering notes. Terms: " + vocabulary.joinToString(", ") + "."
    }
}
