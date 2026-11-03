package com.maheshreddy.mym.ui.ask

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.ui.components.Avatar
import com.maheshreddy.mym.ui.components.BUTLER_NAME
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens
import com.maheshreddy.mym.core.library.AnswerPart
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.ui.components.PillStyle

/** One question and the butler's reply. */
data class Exchange(val id: Int, val question: String, val reply: Reply)

sealed interface Reply {
    data class Thinking(val status: String) : Reply
    data class Answer(val parts: List<AnswerPart>, val citedCount: Int) : Reply
    data object Nothing : Reply
    data class Failed(val message: String) : Reply
}

data class AskUiState(
    val exchanges: List<Exchange> = emptyList(),
    val listening: Boolean = false,
    /** Privacy levels whose entries Ask may use. */
    val include: Set<Privacy> = Privacy.entries.toSet(),
    /** 3–4 questions drawn from the user's own topics (empty state). */
    val suggestions: List<String> = emptyList(),
    val libraryEmpty: Boolean = false,
    val speakAnswers: Boolean = false,
)

@Composable
fun AskScreen(
    initialQuestion: String?,
    onOpenEntry: (Long) -> Unit,
    onInterview: (String) -> Unit,
    vm: AskViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialQuestion) { if (!initialQuestion.isNullOrBlank()) vm.askOnce(initialQuestion) }
    val context = LocalContext.current
    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.toggleVoice() }
    AskContent(
        state, onAsk = vm::ask, onOpenEntry = onOpenEntry, onInterview = onInterview,
        onTogglePrivacy = vm::togglePrivacy, onToggleSpeak = { vm.toggleSpeak() },
        onVoice = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleVoice()
            else requestMic.launch(Manifest.permission.RECORD_AUDIO)
        },
    )
}

/** design-spec §4 Ask: question bubbles, cited answers with numbered chips, and "Start an interview" when empty. */
@Composable
fun AskContent(
    state: AskUiState,
    onAsk: (String) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onInterview: (String) -> Unit,
    onVoice: () -> Unit,
    onTogglePrivacy: (Privacy) -> Unit = {},
    onToggleSpeak: () -> Unit = {},
) {
    val c = Mind.colors
    val list = rememberLazyListState()
    LaunchedEffect(state.exchanges.size) { if (state.exchanges.isNotEmpty()) list.animateScrollToItem(state.exchanges.size) }
    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        LazyColumn(
            Modifier.weight(1f).statusBarsPadding().testTag("ask"),
            state = list,
            contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, top = 28.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenTitle("Ask") }
            item { AskFilters(state, onTogglePrivacy, onToggleSpeak) }
            if (state.exchanges.isEmpty()) item {
                if (state.libraryEmpty) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Caption("Ask answers only from what you've told $BUTLER_NAME, and cites the entries it used. Your library is empty so far: talk for a few minutes and $BUTLER_NAME will file what you say.")
                        Row { Pill("Start a session", { onInterview("") }, Modifier.testTag("askStartSession")) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Caption("Ask anything you've told $BUTLER_NAME. Answers cite the entries they come from. Try:")
                        state.suggestions.forEach { q ->
                            Text(
                                q, style = Mind.type.body, color = c.ink,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                                    .border(1.dp, c.line, RoundedCornerShape(14.dp))
                                    .clickable(role = Role.Button) { onAsk(q) }.padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("suggestion"),
                            )
                        }
                    }
                }
            }
            items(state.exchanges, key = { it.id }) { ex ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            ex.question, style = Mind.type.body, color = c.onInk,
                            modifier = Modifier.widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                                .background(c.ink).padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                    ReplyView(ex, onOpenEntry, onInterview)
                }
            }
        }
        AskBar(state.listening, onAsk, onVoice)
    }
}

/** Which privacy levels Ask may draw on, and whether answers are read aloud. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskFilters(state: AskUiState, onTogglePrivacy: (Privacy) -> Unit, onToggleSpeak: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Privacy.entries.forEach { p ->
            val on = p in state.include
            Pill(
                p.label, { onTogglePrivacy(p) },
                Modifier.testTag("include_${p.name}").semantics { contentDescription = "${p.label} entries ${if (on) "included" else "excluded"}" },
                style = if (on) PillStyle.INK else PillStyle.OUTLINE, height = 34.dp,
            )
        }
        Pill(
            if (state.speakAnswers) "Speaking answers" else "Speak answers", onToggleSpeak, Modifier.testTag("speakAnswers"),
            style = if (state.speakAnswers) PillStyle.INK else PillStyle.OUTLINE, height = 34.dp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyView(ex: Exchange, onOpenEntry: (Long) -> Unit, onInterview: (String) -> Unit) {
    val c = Mind.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(BUTLER_NAME.take(1).uppercase(), onClick = null, size = 26.dp)
            Caption(
                when (val r = ex.reply) {
                    is Reply.Thinking -> r.status
                    is Reply.Answer -> when (r.citedCount) {
                        0 -> "From your entries (no citation given)"
                        1 -> "From 1 of your entries"
                        else -> "From ${r.citedCount} of your entries"
                    }
                    Reply.Nothing -> "Nothing on the shelves"
                    is Reply.Failed -> "Couldn't answer"
                },
            )
        }
        when (val r = ex.reply) {
            is Reply.Thinking -> LinearProgressIndicator(Modifier.fillMaxWidth(0.5f), color = c.accent, trackColor = c.line)
            is Reply.Answer -> FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Words flow as separate pieces so the numbered citation chips sit inline with the text.
                r.parts.forEach { part ->
                    when (part) {
                        is AnswerPart.Text -> part.text.split(Regex("(?<=\\s)")).filter { it.isNotEmpty() }.forEach { w ->
                            Text(w, style = Mind.type.reading.copy(fontSize = Mind.type.reading.fontSize.times(19f / 18f)), color = c.ink)
                        }
                        is AnswerPart.Cite -> Text(
                            "${part.number}", style = Mind.type.chip.copy(fontSize = Mind.type.caption.fontSize), color = c.accent,
                            modifier = Modifier.padding(start = 4.dp, end = 1.dp).align(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(c.accentSoft)
                                .clickable(role = Role.Button) { onOpenEntry(part.entryId) }.padding(horizontal = 6.dp, vertical = 2.dp)
                                .semantics { contentDescription = "Source ${part.number}" },
                        )
                    }
                }
            }
            Reply.Nothing -> {
                Text("Nothing in your entries covers that yet. Shall I interview you on it now?", style = Mind.type.reading.copy(fontSize = Mind.type.reading.fontSize.times(19f / 18f)), color = c.ink)
                Pill("Start an interview on this", { onInterview(ex.question) }, Modifier.testTag("startInterview"))
            }
            is Reply.Failed -> Text(r.message, style = Mind.type.body, color = c.ink)
        }
    }
}

@Composable
private fun AskBar(listening: Boolean, onAsk: (String) -> Unit, onVoice: () -> Unit) {
    val c = Mind.colors
    var text by rememberSaveable { mutableStateOf("") }
    val send = { if (text.isNotBlank()) { onAsk(text); text = "" } }
    Row(
        Modifier.fillMaxWidth().background(c.ground).border(width = 0.dp, color = c.ground).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(c.surface)
                .border(1.dp, c.fieldLine, RoundedCornerShape(23.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (text.isEmpty()) Text(if (listening) "Listening… tap the mic when done" else "Ask your library…", style = Mind.type.body, color = c.muted)
            BasicTextField(
                text, { text = it }, singleLine = true, textStyle = Mind.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth().testTag("askField").semantics { contentDescription = "Ask your library" },
            )
        }
        val icon = when {
            text.isNotBlank() -> Icons.AutoMirrored.Filled.Send
            listening -> Icons.Default.Stop
            else -> Icons.Default.Mic
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (listening) c.record else c.accent)
                .clickable(role = Role.Button) { if (text.isNotBlank()) send() else onVoice() }
                .semantics { contentDescription = if (text.isNotBlank()) "Ask" else if (listening) "Stop listening" else "Ask by voice" },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp)) }
    }
}
