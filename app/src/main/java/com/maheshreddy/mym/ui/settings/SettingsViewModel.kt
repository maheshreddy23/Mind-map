package com.maheshreddy.mym.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.BuildConfig
import com.maheshreddy.mym.audio.SpeechInput
import com.maheshreddy.mym.core.llm.ButlerPrompt
import com.maheshreddy.mym.core.llm.ChatMessage
import com.maheshreddy.mym.core.llm.Role
import com.maheshreddy.mym.data.BackupManager
import com.maheshreddy.mym.data.ButlerRepository
import com.maheshreddy.mym.data.settings.AppSettings
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.device.PerfStats
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.engine.LlamaLanguageModel
import com.maheshreddy.mym.models.InstalledModel
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import com.maheshreddy.mym.nudges.NudgeScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/** One line in Settings → Storage. */
data class StorageLine(val label: String, val bytes: Long)

/** One line in Settings → About → Licences. */
data class LicenceLine(val component: String, val licence: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    private val engines: Engines,
    private val perfStats: PerfStats,
    private val store: ModelStore,
    private val butler: ButlerRepository,
    private val backup: BackupManager,
    private val scheduler: NudgeScheduler,
    private val speech: SpeechInput,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val perf = perfStats.perf
    val models: StateFlow<List<InstalledModel>> = store.models
    val butlerName = butler.profile.map { it.displayName }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "M")

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val _benchmark = MutableStateFlow<String?>(null)
    val benchmark = _benchmark.asStateFlow()

    private val _micTest = MutableStateFlow<String?>(null)
    /** Result of "Test mic": the transcript, or what went wrong. */
    val micTest = _micTest.asStateFlow()
    val micLevel = speech.level

    private val _voices = MutableStateFlow<List<String>>(emptyList())
    val systemVoices = _voices.asStateFlow()

    private val _storage = MutableStateFlow<List<StorageLine>>(emptyList())
    val storage = _storage.asStateFlow()

    val version: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.FLAVOR == "sealed") "Sealed build: no network" else "Full build: network used only for model downloads"}"

    init {
        viewModelScope.launch { _voices.value = runCatching { engines.systemVoices() }.getOrDefault(emptyList()) }
        refreshStorage()
        viewModelScope.launch { speech.transcripts.collect { _micTest.value = "Heard: \"${it.text}\" (%.1f s of audio, transcribed in %.1f s)".format(it.audioSeconds, it.processingSeconds) } }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repo.update(transform)
            scheduler.schedule(repo.settings.first())
        }
    }

    fun activeName(kind: ModelKind): String? = store.active(kind)?.name

    fun refreshStorage() {
        viewModelScope.launch {
            val lines = store.models.value.map { StorageLine(it.name, it.sizeBytes) }.toMutableList()
            if (store.espeakDir.isDirectory) lines += StorageLine("Voice data (espeak-ng, shared)", store.sizeOf(store.espeakDir))
            val dbFiles = listOf("mym.db", "mym.db-wal", "mym.db-shm").map { context.getDatabasePath(it) }
            lines += StorageLine("Your library (entries, sessions, transcripts)", dbFiles.sumOf { if (it.exists()) it.length() else 0 })
            lines += StorageLine("Search index", File(context.filesDir, "search.db").length())
            lines += StorageLine("Cache", store.sizeOf(context.cacheDir))
            _storage.value = lines
        }
    }

    fun clearCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        refreshStorage()
        _message.value = "Cache cleared."
    }

    /** Settings → Speech recognition → Test mic: records 4 s and shows what whisper heard. */
    fun testMic() {
        viewModelScope.launch {
            _micTest.value = "Listening for 4 seconds… say something with your jargon in it."
            speech.start(viewModelScope, continuous = false)
            delay(4_000)
            speech.stop()
            _micTest.value = "Transcribing…"
            if (withTimeoutOrNull(60_000) { speech.transcripts.first() } == null) _micTest.value = "Nothing was heard. Check the microphone permission and the speech model."
        }
    }

    /** Settings → Voice → Play sample. */
    fun playSample() {
        viewModelScope.launch {
            val prefs = repo.settings.first()
            val name = butler.profile.first().displayName
            runCatching {
                val synth = engines.synthesizer(preferPiper = prefs.voice == "piper")
                synth.configure(prefs.pitch, prefs.systemVoice)
                synth.speak("Good evening. I'm $name. Shall we shelve something you learned today?", prefs.speechRate)
            }.onFailure { _message.value = "Could not play the sample: ${it.message}" }
        }
    }

    /** Measures time to first token with the butler's real persona prompt (cold cache, as at a session's start). */
    fun runLlmBenchmark() {
        viewModelScope.launch {
            _benchmark.value = "Loading model…"
            runCatching {
                val engine = engines.llm()
                val model = LlamaLanguageModel(engine)
                val messages = listOf(
                    ChatMessage(Role.SYSTEM, ButlerPrompt.system(butler.profile.first(), 40)),
                    ChatMessage(Role.USER, "In final assembly we use drill jigs located on tooling holes so every hole lands in the same place."),
                )
                engine.abort()
                _benchmark.value = "Generating…"
                val t0 = System.nanoTime()
                var first = -1L
                var chunks = 0
                model.stream(messages, maxTokens = 48, temperature = 0.7f).collect {
                    if (first < 0) first = (System.nanoTime() - t0) / 1_000_000
                    chunks++
                }
                perfStats.recordLlm(first, chunks, (System.nanoTime() - t0) / 1_000_000)
                "First token in $first ms (target under 2,000 ms) with ${store.active(ModelKind.LLM)?.name ?: "the active model"}"
            }.onSuccess { _benchmark.value = it }.onFailure { _benchmark.value = "Benchmark failed: ${it.message}" }
        }
    }

    fun exportBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            runCatching { backup.export(uri, passphrase.toCharArray()) }
                .onSuccess { _message.value = "Backup saved and encrypted. Keep the passphrase: without it the file cannot be opened." }
                .onFailure { _message.value = "Backup failed: ${it.message}" }
        }
    }

    private val _restartNeeded = MutableStateFlow(false)
    val restartNeeded = _restartNeeded.asStateFlow()

    fun importBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            runCatching { backup.import(uri, passphrase.toCharArray()) }
                .onSuccess { _restartNeeded.value = true }
                .onFailure { _message.value = "Restore failed: ${it.message}" }
        }
    }

    /** Licences of the installed models and the app's open-source parts. */
    fun licences(): List<LicenceLine> =
        store.models.value.map { m -> LicenceLine(m.name, m.catalog?.licence?.let { l -> l.name + (l.url?.let { " · $it" } ?: "") } ?: "Imported file: licence not known to MYM") } +
            listOf(
                LicenceLine("whisper.cpp, llama.cpp (speech and language engines)", "MIT"),
                LicenceLine("sherpa-onnx (Piper voices)", "Apache 2.0"),
                LicenceLine("espeak-ng data (pronunciation, used by Piper voices)", "GPL-3.0"),
                LicenceLine("Newsreader and Inter fonts", "SIL Open Font License 1.1"),
                LicenceLine("AndroidX, Jetpack Compose, Room, WorkManager, Hilt", "Apache 2.0"),
            )
}
