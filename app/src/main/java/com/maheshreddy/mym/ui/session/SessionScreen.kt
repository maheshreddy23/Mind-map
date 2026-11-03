package com.maheshreddy.mym.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.audio.ListenState
import com.maheshreddy.mym.engine.EngineStatus
import com.maheshreddy.mym.engine.LoadState
import com.maheshreddy.mym.session.FresherState
import com.maheshreddy.mym.ui.ConfirmDialog
import com.maheshreddy.mym.ui.components.BUTLER_NAME
import com.maheshreddy.mym.ui.components.BackBar
import com.maheshreddy.mym.ui.theme.FocusColors
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens
import com.maheshreddy.mym.core.Speaker

/** One transcript line as the focus screen shows it. */
data class SessionTurn(val id: Long, val speaker: Speaker, val text: String, val timing: TurnTiming?)

/** Everything the focus screen draws; built from the view model, or by hand in screenshot tests. */
data class SessionUiState(
    val title: String,
    val ended: Boolean,
    val listenOnly: Boolean = false,
    /** Set when an unfinished session is reopened after a while: offer Resume or Wrap up. */
    val resumePrompt: Boolean = false,
    val turns: List<SessionTurn>,
    val listen: ListenState,
    val level: Float,
    val butler: FresherState,
    val engines: EngineStatus,
    val deviceWarning: String?,
    /** After wrap-up: NONE / PENDING / DONE / FAILED and how many entries came out. */
    val notesStatus: String,
    val filed: Int,
    val waiting: Int,
    /** Wrap up was tapped: filing starts after a 5 s window in which it can be undone. */
    val wrapPending: Boolean = false,
)

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenEntry: (Long) -> Unit = {},
    onOpenTopic: (Long) -> Unit = {},
    vm: SessionViewModel = hiltViewModel(),
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val turns by vm.turns.collectAsStateWithLifecycle()
    val timings by vm.timings.collectAsStateWithLifecycle()
    val listen by vm.listenState.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val butler by vm.fresherState.collectAsStateWithLifecycle()
    val engines by vm.engineStatus.collectAsStateWithLifecycle()
    val warning by vm.deviceWarning.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val listenOnly by vm.listenOnly.collectAsStateWithLifecycle()
    val resumePrompt by vm.resumePrompt.collectAsStateWithLifecycle()
    val closed by vm.closed.collectAsStateWithLifecycle()
    LaunchedEffect(closed) { if (closed) onBack() }
    val ended = session?.endedAt != null
    // Wrap-up (button or saying "wrap up") opens the filing results as a sheet, once, for a session that was live here.
    var wasLive by rememberSaveable { mutableStateOf(false) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session?.id, ended) {
        val s = session ?: return@LaunchedEffect
        if (s.endedAt == null) wasLive = true else if (wasLive) { wasLive = false; showResults = true }
    }
    // Tapping Wrap up waits 5 s (Undo) before filing starts.
    var wrapPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wrapPending) {
        if (wrapPending) {
            kotlinx.coroutines.delay(WRAP_UNDO_MS)
            wrapPending = false
            vm.wrapUp()
        }
    }
    if (showResults) {
        FilingResultsSheet(onClose = { showResults = false; onHome() }, onOpenEntry = onOpenEntry, onOpenTopic = onOpenTopic)
    }
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        RenameDialog(session?.title.orEmpty(), onConfirm = { vm.rename(it); renaming = false }, onDismiss = { renaming = false })
    }

    // Keep the screen on while a session is live so the system does not cut the microphone.
    val view = LocalView.current
    DisposableEffect(ended) {
        view.keepScreenOn = !ended
        onDispose { view.keepScreenOn = false }
    }
    val context = LocalContext.current
    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.toggleMic() }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this session?",
            text = "The transcript and every entry filed from it are removed for good. Use this for test or junk sessions.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; vm.deleteSession(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }

    SessionContent(
        state = SessionUiState(
            title = session?.title ?: "Session",
            ended = ended,
            listenOnly = listenOnly,
            resumePrompt = resumePrompt,
            turns = turns.map { SessionTurn(it.id, runCatching { Speaker.valueOf(it.speaker) }.getOrDefault(Speaker.SYSTEM), it.text, timings[it.id]) },
            listen = listen, level = level, butler = butler, engines = engines, deviceWarning = warning,
            notesStatus = session?.notesStatus ?: "NONE",
            filed = entries.count { it.status == "FILED" }, waiting = entries.count { it.status == "INBOX" },
            wrapPending = wrapPending,
        ),
        onBack = onBack,
        onWrapUp = { wrapPending = true },
        onMic = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleMic()
            else requestMic.launch(Manifest.permission.RECORD_AUDIO)
        },
        onSend = vm::submitText,
        onDismissError = vm::dismissError,
        onDelete = { confirmDelete = true },
        onOpenResults = { showResults = true },
        onRetry = vm::retryFiling,
        onMode = vm::setListenOnly,
        onResume = vm::resume,
        onRename = { renaming = true },
        onUndoWrap = { wrapPending = false },
    )
}

const val WRAP_UNDO_MS = 5_000L

/** Focus mode (design-spec §4 Session): always dark; only the transcript, the butler's question and the mic. */
@Composable
fun SessionContent(
    state: SessionUiState,
    onBack: () -> Unit,
    onWrapUp: () -> Unit,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    onDismissError: () -> Unit,
    onDelete: () -> Unit,
    onOpenResults: () -> Unit,
    onRetry: () -> Unit,
    onMode: (Boolean) -> Unit = {},
    onResume: () -> Unit = {},
    onRename: () -> Unit = {},
    onUndoWrap: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(FocusColors.bg).imePadding()) {
        var menu by remember { mutableStateOf(false) }
        // Top bar: back, Listen/Interview, overflow (rename, delete). Wrap up lives in the input row.
        BackBar(onBack, tint = FocusColors.text) {
            if (!state.ended) ModeSwitch(state.listenOnly, onMode)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("sessionMenu")) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = FocusColors.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() }, modifier = Modifier.testTag("renameSession"))
                    DropdownMenuItem(text = { Text("Delete session") }, onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("deleteSession"))
                }
            }
        }
        Text(
            when {
                state.ended -> state.title
                state.listenOnly -> "$BUTLER_NAME stays quiet and just captures."
                else -> "$BUTLER_NAME asks one sharp follow-up at a time."
            },
            style = Mind.type.caption, color = FocusColors.muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (state.resumePrompt && !state.ended) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(FocusColors.controlBg).padding(16.dp).testTag("resumePrompt"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("This session is still open. Carry on, or wrap it up and file what you said?", style = Mind.type.body, color = FocusColors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusPill("Resume", onResume)
                    FocusPill("Wrap up", onWrapUp)
                }
            }
        }
        Transcript(state, Modifier.weight(1f))
        if (!state.ended) {
            EngineLine(state.engines)
            ButlerLine(state.butler, onDismissError)
            state.deviceWarning?.let { Text(it, style = Mind.type.caption, color = FocusColors.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            MicArea(state.listen, state.level, onMic)
            if (state.wrapPending) WrapUndoBar(onUndoWrap)
            TypedInput(onSend, onWrapUp, wrapPending = state.wrapPending)
        } else {
            FiledCard(state, onOpenResults, onRetry)
        }
    }
}

/** Listen / Interview segmented control (design-spec §4 Session). */
@Composable
private fun ModeSwitch(listenOnly: Boolean, onMode: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(FocusColors.controlBg).padding(4.dp).testTag("modeSwitch"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("Listen" to true, "Interview" to false).forEach { (label, listen) ->
            val on = listen == listenOnly
            Box(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) FocusColors.text else FocusColors.transparent)
                    .clickable(role = Role.RadioButton) { onMode(listen) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) Mind.type.button.copy(fontSize = Mind.type.label.fontSize) else Mind.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) FocusColors.bg else FocusColors.offSegment)
            }
        }
    }
}

@Composable
private fun FocusPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = MindDimens.touch).clip(RoundedCornerShape(22.dp)).border(1.dp, FocusColors.line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Mind.type.button, color = FocusColors.text) }
}

@Composable
private fun Transcript(state: SessionUiState, modifier: Modifier) {
    val listState = rememberLazyListState()
    val live = state.listen is ListenState.Listening || state.listen == ListenState.Transcribing
    LaunchedEffect(state.turns.size, live) {
        val last = state.turns.size + (if (live) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    LazyColumn(
        modifier.fillMaxWidth().testTag("transcript"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    ) {
        if (state.turns.isEmpty() && !live) item {
            Text(
                "Talk about anything you know. $BUTLER_NAME listens, asks, and files it when you wrap up.",
                style = Mind.type.bodyLarge, color = FocusColors.muted,
            )
        }
        items(state.turns, key = { it.id }) { TurnItem(it) }
        if (live) item(key = "live") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline("You · ${if (state.listen == ListenState.Transcribing) "transcribing" else "listening"}", FocusColors.muted)
                Text(if (state.listen == ListenState.Transcribing) "Transcribing…" else "Listening…", style = Mind.type.bodyLarge, color = FocusColors.muted)
            }
        }
    }
}

@Composable
private fun TurnItem(turn: SessionTurn) {
    when (turn.speaker) {
        Speaker.FRESHER -> Column(
            Modifier.fillMaxWidth().drawBehind {
                drawLine(FocusColors.questionRule, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
            }.padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Overline("$BUTLER_NAME asks", FocusColors.accent)
            Text(turn.text, style = Mind.type.question, color = FocusColors.text)
        }
        Speaker.SENIOR -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Overline(
                turn.timing?.let { "You · %.1f s · transcribed in %.1f s".format(it.audioSeconds, it.processingSeconds) } ?: "You",
                FocusColors.muted, Modifier.testTag("turnTiming"),
            )
            Text(turn.text, style = Mind.type.bodyLarge, color = FocusColors.text)
        }
        Speaker.SYSTEM -> Text(turn.text, style = Mind.type.caption, color = FocusColors.muted)
    }
}

@Composable
private fun Overline(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = Mind.type.overline, color = color, modifier = modifier)

/** "Loading speech model…" until both engines are ready (v1 behaviour). */
@Composable
private fun EngineLine(status: EngineStatus) {
    val failed = status.whisper == LoadState.FAILED || status.llm == LoadState.FAILED
    val text = when {
        status.whisper == LoadState.LOADING -> "Loading speech model…"
        status.llm == LoadState.LOADING -> "Loading language model… you can already speak"
        failed -> status.error?.let { "Model failed to load: $it" }
        else -> null
    } ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag("engineBanner"), verticalAlignment = Alignment.CenterVertically) {
        if (!failed) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = FocusColors.accent)
        Text(text, Modifier.padding(start = 8.dp), style = Mind.type.caption, color = if (failed) FocusColors.text else FocusColors.muted)
    }
}

@Composable
private fun ButlerLine(state: FresherState, onDismiss: () -> Unit) {
    val text = when (state) {
        FresherState.Idle, FresherState.WrappedUp -> return
        FresherState.Summarizing -> "$BUTLER_NAME is condensing the conversation so far…"
        is FresherState.Thinking -> if (state.partial.isBlank()) "$BUTLER_NAME is thinking…" else state.partial
        is FresherState.Speaking -> "$BUTLER_NAME is asking…"
        is FresherState.Error -> state.message
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(FocusColors.controlBg)
            .padding(horizontal = 14.dp, vertical = 10.dp).testTag("fresherStatus"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = Mind.type.body, color = if (state is FresherState.Thinking && state.partial.isNotBlank()) FocusColors.text else FocusColors.muted, maxLines = 3)
        if (state is FresherState.Error) Text("OK", style = Mind.type.button, color = FocusColors.accent, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
    }
}

/** Live waveform (driven by the real input level), the 76 dp mic with a halo while recording, and a hint. */
@Composable
private fun MicArea(listen: ListenState, level: Float, onMic: () -> Unit) {
    val recording = listen is ListenState.Listening
    val pulse = rememberInfiniteTransition(label = "halo")
    val halo by pulse.animateFloat(10f, 16f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "halo")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Waveform(if (recording) level else 0f)
        val color = if (recording) FocusColors.record else FocusColors.idleMic
        Box(
            Modifier.size(MindDimens.micButton + 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(MindDimens.micButton + (if (recording) halo else 10f).dp * 2).clip(CircleShape)
                    .background((if (recording) FocusColors.record else FocusColors.accent).copy(alpha = if (recording) 0.18f else 0.12f)),
            )
            Box(
                Modifier.size(MindDimens.micButton).clip(CircleShape).background(color)
                    .clickable(role = Role.Button, onClick = onMic)
                    .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" }
                    .testTag("mic"),
                contentAlignment = Alignment.Center,
            ) {
                if (recording) Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(FocusColors.onMic))
                else Icon(Icons.Default.Mic, contentDescription = null, tint = FocusColors.onMic, modifier = Modifier.size(30.dp))
            }
        }
        Text(
            when (listen) {
                ListenState.Idle -> "Tap to speak"
                is ListenState.Listening -> when {
                    !listen.continuous -> "Listening. Tap to finish this thought."
                    listen.hearingSpeech -> "Hearing you…"
                    else -> "Listening continuously"
                }
                ListenState.LoadingModel -> "Loading speech model… your recording is kept"
                ListenState.Transcribing -> "Transcribing…"
                is ListenState.Error -> listen.message
            },
            style = Mind.type.body, color = FocusColors.muted,
        )
    }
}

private val BAR_SHAPE = floatArrayOf(0.25f, 0.4f, 0.65f, 0.9f, 0.55f, 0.75f, 1f, 0.6f, 0.35f, 0.7f, 0.95f, 0.45f, 0.8f, 0.6f, 0.3f, 0.5f, 0.75f, 0.35f)

@Composable
private fun Waveform(level: Float) {
    Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BAR_SHAPE.forEach { f ->
            val h = (4f + 32f * f * level).coerceIn(4f, 36f)
            Box(Modifier.width(4.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(if (level > 0.02f) FocusColors.accent else FocusColors.line))
        }
    }
}

/** "Wrapping up… Undo" for [WRAP_UNDO_MS] after Wrap up is tapped. */
@Composable
private fun WrapUndoBar(onUndo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(FocusColors.text).padding(start = 16.dp).testTag("wrapSnackbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Wrapping up…", style = Mind.type.body, color = FocusColors.bg, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onUndo, modifier = Modifier.testTag("undoWrap")) {
            Text("Undo", style = Mind.type.button, color = FocusColors.accent)
        }
    }
}

/**
 * Typed input. The action button is context-sensitive: Send while there is text, otherwise Wrap up (a check).
 * Saying "wrap up" keeps working through the conversation loop.
 */
@Composable
private fun TypedInput(onSend: (String) -> Unit, onWrapUp: () -> Unit, wrapPending: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp)).background(FocusColors.controlBg).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) Text("Type instead of speaking…", style = Mind.type.body, color = FocusColors.muted)
            BasicTextField(
                value = text, onValueChange = { text = it }, textStyle = Mind.type.body.copy(color = FocusColors.text),
                cursorBrush = SolidColor(FocusColors.accent), modifier = Modifier.fillMaxWidth().testTag("typedInput"),
            )
        }
        if (text.isNotBlank()) {
            IconButton(onClick = { onSend(text); text = "" }, modifier = Modifier.testTag("send")) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = FocusColors.accent)
            }
        } else {
            IconButton(onClick = onWrapUp, enabled = !wrapPending, modifier = Modifier.testTag("wrapUp")) {
                Box(
                    Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (wrapPending) FocusColors.line else FocusColors.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, contentDescription = "Wrap up", tint = FocusColors.bg, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.testTag("renameField"))
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Filing results after wrap-up, inside the session: needs-you items (File / Change / Discard) and Undo. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FilingResultsSheet(onClose: () -> Unit, onOpenEntry: (Long) -> Unit, onOpenTopic: (Long) -> Unit) {
    val vm: com.maheshreddy.mym.ui.library.InboxViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.allTopics.collectAsStateWithLifecycle()
    var changing by remember { mutableStateOf<com.maheshreddy.mym.data.db.EntryEntity?>(null) }
    changing?.let { e -> com.maheshreddy.mym.ui.library.ChangeFilingDialog(e, subjects, topics, onConfirm = { vm.file(e, it); changing = null }, onDismiss = { changing = null }) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mind.colors.ground,
        modifier = Modifier.testTag("filingSheet"),
    ) {
        com.maheshreddy.mym.ui.library.FilingResults(
            state.copy(needs = state.needs.filter { it.entry.sessionId == vm.sessionId }),
            onOpenEntry = onOpenEntry,
            onOpenTopic = { e -> e.topicId?.let(onOpenTopic) },
            onAccept = vm::accept, onChange = { changing = it }, onDiscard = vm::reject, onUndo = vm::undo,
            onDone = onClose,
        )
    }
}

@Composable
private fun FiledCard(state: SessionUiState, onOpenResults: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(FocusColors.controlBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when (state.notesStatus) {
                "DONE" -> if (state.filed + state.waiting == 0) "Nothing to file from this session." else "Filed ${state.filed}. ${state.waiting} waiting for your call."
                "FAILED" -> "Filing failed. It is retried on next launch."
                "PENDING" -> "$BUTLER_NAME is filing this session…"
                else -> "This session has ended."
            },
            style = Mind.type.body, color = FocusColors.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.filed + state.waiting > 0) FocusPill("See results", onOpenResults)
            if (state.notesStatus == "FAILED") FocusPill("Retry now", onRetry)
        }
        Spacer(Modifier.height(0.dp))
    }
}
