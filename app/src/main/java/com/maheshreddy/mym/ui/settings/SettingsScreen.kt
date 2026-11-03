package com.maheshreddy.mym.ui.settings

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.data.settings.AppSettings
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.ui.components.BackBar
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.MindCard
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.PillStyle
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.components.SectionLabel
import com.maheshreddy.mym.ui.models.ModelsViewModel.Companion.sizeLabel
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the Settings screen draws (stateless content for screenshots). */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val butlerName: String = "M",
    val activeModels: Map<ModelKind, String?> = emptyMap(),
    val systemVoices: List<String> = emptyList(),
    val micTest: String? = null,
    val benchmark: String? = null,
    val lastStt: String? = null,
    val lastReply: String? = null,
    val storage: List<StorageLine> = emptyList(),
    val version: String = "",
    val licences: List<LicenceLine> = emptyList(),
    val biometricAvailable: Boolean = true,
)

/** The actions Settings can take. */
class SettingsActions(
    val update: ((AppSettings) -> AppSettings) -> Unit = {},
    val openModels: (String?) -> Unit = {},
    val openButler: () -> Unit = {},
    val testMic: () -> Unit = {},
    val playSample: () -> Unit = {},
    val benchmark: () -> Unit = {},
    val clearCache: () -> Unit = {},
    val exportBackup: () -> Unit = {},
    val importBackup: () -> Unit = {},
    val pickTime: (current: Int, set: (Int) -> Unit) -> Unit = { _, _ -> },
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenModels: (String?) -> Unit, onOpenButler: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val name by vm.butlerName.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val voices by vm.systemVoices.collectAsStateWithLifecycle()
    val mic by vm.micTest.collectAsStateWithLifecycle()
    val bench by vm.benchmark.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val restart by vm.restartNeeded.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var passphraseFor by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) passphraseFor = "export" to uri }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) passphraseFor = "import" to uri }
    passphraseFor?.let { (mode, uri) ->
        PassphraseDialog(
            title = if (mode == "export") "Encrypt the backup" else "Unlock the backup",
            text = if (mode == "export") "Choose a passphrase. You need it to restore; MYM cannot recover it." else "Enter the passphrase used when the backup was made. Restoring replaces your current library.",
            onConfirm = { p -> passphraseFor = null; if (mode == "export") vm.exportBackup(uri, p) else vm.importBackup(uri, p) },
            onDismiss = { passphraseFor = null },
        )
    }
    if (restart) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Library restored") },
            text = { Text("MYM needs to restart to open the restored library.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }) { Text("Restart now") }
            },
        )
    }
    val biometric = remember { androidx.biometric.BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS }

    SettingsContent(
        state = SettingsUiState(
            settings = s, butlerName = name,
            activeModels = models.let { ModelKind.entries.associateWith { k -> vm.activeName(k) } },
            systemVoices = voices, micTest = mic, benchmark = bench,
            lastStt = perf.sttRealTimeFactor?.let { "Last transcription: %.1f s of audio in %.1f s (%.2f× real time)".format(perf.sttAudioSeconds, perf.sttProcessingSeconds, it) },
            lastReply = perf.llmFirstTokenMs?.let { "Last reply: first token in $it ms" + (perf.llmTokensPerSecond?.let { t -> ", %.1f tokens/s".format(t) } ?: "") },
            storage = storage, version = vm.version, licences = vm.licences(), biometricAvailable = biometric,
        ),
        snackbar = snackbar,
        onBack = onBack,
        actions = SettingsActions(
            update = vm::update,
            openModels = onOpenModels,
            openButler = onOpenButler,
            testMic = vm::testMic,
            playSample = vm::playSample,
            benchmark = vm::runLlmBenchmark,
            clearCache = vm::clearCache,
            exportBackup = { createBackup.launch("MYM-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".mymb") },
            importBackup = { openBackup.launch(arrayOf("*/*")) },
            pickTime = { current, set -> TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, true).show() },
        ),
    )
}

/** Fingerprint/face, or the phone's screen-lock PIN/pattern/password. */
const val LOCK_AUTHENTICATORS = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(state: SettingsUiState, snackbar: SnackbarHostState, onBack: () -> Unit, actions: SettingsActions) {
    val c = Mind.colors
    val s = state.settings
    val set = actions.update
    Scaffold(topBar = { BackBar(onBack) }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = c.ground) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("settings"),
            contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScreenTitle("Settings") }

            item {
                Section("Models") {
                    ModelKind.entries.forEach { k ->
                        LinkRow(k.category.label, state.activeModels[k] ?: if (k.required) "Not installed: needed" else "None (optional)") { actions.openModels(k.category.name) }
                    }
                    Pill("Open the model manager", { actions.openModels(null) }, Modifier.fillMaxWidth(), height = MindDimens.touch)
                }
            }

            item {
                Section("Speech recognition") {
                    LinkRow("Active Whisper model", state.activeModels[ModelKind.WHISPER] ?: "Not installed") { actions.openModels("SPEECH") }
                    Choice("Language", listOf(false to "English", true to "Multilingual"), s.multilingual) { v -> set { it.copy(multilingual = v) } }
                    Caption("Multilingual needs a multilingual Whisper model; English-only models always transcribe English.")
                    Toggle("Continuous listening (detects when you stop speaking)", s.continuousMode) { v -> set { it.copy(continuousMode = v) } }
                    Vocabulary(s.vocabulary) { list -> set { it.copy(vocabulary = list) } }
                    Pill("Test mic", actions.testMic, style = PillStyle.OUTLINE, height = MindDimens.touch)
                    state.micTest?.let { Text(it, style = Mind.type.body, color = c.ink) }
                }
            }

            item {
                Section("Voice") {
                    Choice("Engine", listOf("system" to "Android TTS", "piper" to "Piper"), s.voice) { v -> set { it.copy(voice = v) } }
                    if (s.voice == "piper") {
                        LinkRow("Piper voice", state.activeModels[ModelKind.TTS] ?: "None installed: falls back to Android TTS") { actions.openModels("VOICE") }
                    } else {
                        Caption("Android voice")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipButton("System default", s.systemVoice.isEmpty()) { set { it.copy(systemVoice = "") } }
                            state.systemVoices.take(12).forEach { v -> ChipButton(v.substringAfter("en-").take(28), s.systemVoice == v) { set { it.copy(systemVoice = v) } } }
                        }
                    }
                    Lever("Speed", "%.1f×".format(s.speechRate), s.speechRate, 0.5f..2f) { v -> set { it.copy(speechRate = v) } }
                    Lever("Pitch", "%.1f×".format(s.pitch), s.pitch, 0.5f..2f) { v -> set { it.copy(pitch = v) } }
                    if (s.voice == "piper") Caption("Pitch applies to Android TTS voices; Piper voices have a fixed pitch.")
                    Toggle("Speak Ask answers aloud", s.speakAnswers) { v -> set { it.copy(speakAnswers = v) } }
                    Pill("Play sample", actions.playSample, style = PillStyle.OUTLINE, height = MindDimens.touch)
                }
            }

            item {
                Section("Butler") {
                    LinkRow(state.butlerName, "Name, personality, interview style and journal", actions.openButler)
                    Lever("Question length", "${s.maxReplyWords} words max", s.maxReplyWords.toFloat(), 15f..60f) { v -> set { it.copy(maxReplyWords = v.toInt()) } }
                    Lever("Ask after every", if (s.questionEveryNTurns == 1) "turn" else "${s.questionEveryNTurns} turns", s.questionEveryNTurns.toFloat(), 1f..5f) { v -> set { it.copy(questionEveryNTurns = v.toInt()) } }
                }
            }

            item {
                Section("Nudges") {
                    Toggle("Unfinished topics (open questions)", s.nudgeUnfinished) { v -> set { it.copy(nudgeUnfinished = v) } }
                    Toggle("Look back (\"three months ago you said…\")", s.nudgeResurface) { v -> set { it.copy(nudgeResurface = v) } }
                    Toggle("Daily capture prompt", s.nudgeDaily) { v -> set { it.copy(nudgeDaily = v) } }
                    LinkRow("Daily nudge at", clock(s.nudgeTimeMin)) { actions.pickTime(s.nudgeTimeMin) { m -> set { it.copy(nudgeTimeMin = m) } } }
                    LinkRow("Quiet hours", "${clock(s.quietStartMin)} – ${clock(s.quietEndMin)}") {
                        actions.pickTime(s.quietStartMin) { start -> actions.pickTime(s.quietEndMin) { end -> set { it.copy(quietStartMin = start, quietEndMin = end) } } }
                    }
                    Caption("No notifications during quiet hours; a nudge due then waits until they end.")
                }
            }

            item {
                Section("Privacy") {
                    Choice("New entries are", Privacy.entries.map { it to it.label }, s.defaultPrivacy) { v -> set { it.copy(defaultPrivacy = v) } }
                    Toggle("App lock (fingerprint, face or screen-lock PIN)", s.appLock, enabled = state.biometricAvailable) { v -> set { it.copy(appLock = v) } }
                    if (!state.biometricAvailable) Caption("Set up a screen lock in Android settings to use app lock.")
                }
            }

            item {
                Section("Storage") {
                    state.storage.forEach { line ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(line.label, style = Mind.type.body, color = c.ink, modifier = Modifier.weight(1f))
                            Text(sizeLabel(line.bytes), style = Mind.type.body, color = c.muted)
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", style = Mind.type.label, color = c.ink, modifier = Modifier.weight(1f))
                        Text(sizeLabel(state.storage.sumOf { it.bytes }), style = Mind.type.label, color = c.ink)
                    }
                    Pill("Clear cache", actions.clearCache, style = PillStyle.OUTLINE, height = MindDimens.touch)
                }
            }

            item {
                Section("Backup") {
                    Caption("Your whole library, settings and butler, encrypted with a passphrase, saved to a file you choose. Models are not included.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Export backup", actions.exportBackup, height = MindDimens.touch)
                        Pill("Restore", actions.importBackup, style = PillStyle.OUTLINE, height = MindDimens.touch)
                    }
                    Toggle("Download models on Wi-Fi only", s.wifiOnly) { v -> set { it.copy(wifiOnly = v) } }
                }
            }

            item {
                Section("Performance") {
                    Pill("Benchmark the language model", actions.benchmark, style = PillStyle.OUTLINE, height = MindDimens.touch)
                    state.benchmark?.let { Text(it, style = Mind.type.body, color = c.ink) }
                    Caption(state.lastStt ?: "No transcription yet this run.")
                    Caption(state.lastReply ?: "No reply yet this run.")
                }
            }

            item {
                Section("About") {
                    Text("MYM – Map your Mind", style = Mind.type.cardTitle, color = c.ink)
                    Caption(state.version)
                    SectionLabel("Licences")
                    state.licences.forEach { l ->
                        Column {
                            Text(l.component, style = Mind.type.body, color = c.ink)
                            Caption(l.licence)
                        }
                    }
                }
            }
        }
    }
}

private fun clock(min: Int) = "%02d:%02d".format(min / 60, min % 60)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        MindCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
    }
}

@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    val c = Mind.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MindDimens.touch).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Mind.type.body, color = c.ink)
            Caption(value)
        }
        Text("›", style = Mind.type.cardTitle, color = c.muted)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = Mind.colors
    Row(Modifier.fillMaxWidth().heightIn(min = MindDimens.touch), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Mind.type.body, color = if (enabled) c.ink else c.muted, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, text) -> ChipButton(text, v == selected) { onSelect(v) } }
        }
    }
}

@Composable
private fun ChipButton(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Mind.colors
    Box(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) c.ink else c.surface)
            .then(if (on) Modifier else Modifier.border(1.dp, c.fieldLine, RoundedCornerShape(18.dp)))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Mind.type.label.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) c.onInk else c.ink) }
}

@Composable
private fun Lever(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val c = Mind.colors
    Column {
        Row {
            Text(label, style = Mind.type.body, color = c.ink, modifier = Modifier.weight(1f))
            Caption(value)
        }
        Slider(
            value = current, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** Domain vocabulary: words whisper should spell your way (PLM, CCPM, Teamcenter…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Vocabulary(words: List<String>, onChange: (List<String>) -> Unit) {
    val c = Mind.colors
    var draft by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption("Domain vocabulary (helps Whisper spell your jargon)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            words.forEach { w ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(c.neutralChip).clickable(role = Role.Button) { onChange(words - w) }
                        .padding(horizontal = 10.dp, vertical = 6.dp).semantics { contentDescription = "Remove $w" },
                ) { Text("$w  ×", style = Mind.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.neutralChipText) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = MindDimens.touch).clip(RoundedCornerShape(12.dp)).background(c.surface)
                    .border(1.dp, c.fieldLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (draft.isEmpty()) Text("Add a term, e.g. GD&T", style = Mind.type.body, color = c.muted)
                BasicTextField(draft, { draft = it }, singleLine = true, textStyle = Mind.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
            }
            Pill("Add", { if (draft.isNotBlank()) { onChange(words + draft.trim()); draft = "" } }, height = MindDimens.touch)
        }
    }
}

@Composable
private fun PassphraseDialog(title: String, text: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var p by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                androidx.compose.material3.OutlinedTextField(p, { p = it }, singleLine = true, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(p) }, enabled = p.length >= 6) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
