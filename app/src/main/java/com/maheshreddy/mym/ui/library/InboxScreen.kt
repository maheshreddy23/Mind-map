package com.maheshreddy.mym.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.data.db.EntryEntity
import com.maheshreddy.mym.ui.components.BackBar
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.MindCard
import com.maheshreddy.mym.ui.components.NeedsYouChip
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.PillStyle
import com.maheshreddy.mym.ui.components.PrivacyChip
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.components.SectionLabel
import com.maheshreddy.mym.ui.components.TypeChip
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens

data class InboxItem(
    val entry: EntryEntity,
    val subject: String?,
    val topic: String?,
    val newSubject: Boolean,
    val newTopic: Boolean,
    val canAccept: Boolean,
)

data class FiledItem(val entry: EntryEntity, val breadcrumb: String, val undoable: Boolean)

data class InboxUiState(
    val sessionMode: Boolean = false,
    val filing: Boolean = false,
    val failed: Boolean = false,
    val filedFromSession: Int = 0,
    val waitingFromSession: Int = 0,
    val needs: List<InboxItem> = emptyList(),
    /** Entries accepted on this screen, shown as one confirmation line each. */
    val confirmations: List<Pair<Long, String>> = emptyList(),
    val filed: List<FiledItem> = emptyList(),
    /** Sessions still open after 12 h idle. */
    val stale: List<StaleSession> = emptyList(),
    /** Empty state: the last five filed entries (with Undo) and a few stats. */
    val recentFiled: List<FiledItem> = emptyList(),
    val stats: com.maheshreddy.mym.core.library.LibraryStats? = null,
)

data class StaleSession(val id: Long, val title: String)

@Composable
fun InboxScreen(
    onBack: (() -> Unit)?,
    onOpenEntry: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenSession: (Long) -> Unit = {},
    vm: InboxViewModel = hiltViewModel(),
) {
    val state0 by vm.state.collectAsStateWithLifecycle()
    val stale by vm.staleSessions.collectAsStateWithLifecycle()
    val state = state0.copy(stale = stale.map { StaleSession(it.id, it.title) })
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.allTopics.collectAsStateWithLifecycle()
    var filing by remember { mutableStateOf<EntryEntity?>(null) }
    val snackbar = rememberMessages(vm)
    filing?.let { e ->
        FileEntryDialog(
            title = "File \"${e.title}\"",
            subjects = subjects, topics = topics,
            initialSubject = e.proposedSubjectPick(), initialTopic = e.proposedTopicPick(),
            initialType = e.entryType(), initialPrivacy = e.entryPrivacy(),
            onConfirm = { vm.file(e, it); filing = null },
            onDismiss = { filing = null },
        )
    }
    InboxContent(
        state, snackbar, onBack, onOpenEntry,
        onOpenTopic = { e -> e.topicId?.let(onOpenTopic) },
        onAccept = vm::accept, onChange = { filing = it }, onDiscard = vm::reject, onUndo = vm::undo,
        onWrapUpSession = vm::wrapUpSession, onOpenSession = onOpenSession,
    )
}

/** The "Change" dialog for an Inbox entry, pre-filled with the butler's proposal. */
@Composable
fun ChangeFilingDialog(
    e: EntryEntity,
    subjects: List<com.maheshreddy.mym.data.db.SubjectEntity>,
    topics: List<com.maheshreddy.mym.data.db.TopicEntity>,
    onConfirm: (FilingChoice) -> Unit,
    onDismiss: () -> Unit,
) = FileEntryDialog(
    title = "File \"${e.title}\"",
    subjects = subjects, topics = topics,
    initialSubject = e.proposedSubjectPick(), initialTopic = e.proposedTopicPick(),
    initialType = e.entryType(), initialPrivacy = e.entryPrivacy(),
    onConfirm = onConfirm, onDismiss = onDismiss,
)

/** Wrap-up results in the session's bottom sheet: this session's needs-you cards, then what was auto-filed. */
@Composable
fun FilingResults(
    state: InboxUiState,
    onOpenEntry: (Long) -> Unit,
    onOpenTopic: (EntryEntity) -> Unit,
    onAccept: (EntryEntity) -> Unit,
    onChange: (EntryEntity) -> Unit,
    onDiscard: (EntryEntity) -> Unit,
    onUndo: (EntryEntity) -> Unit,
    onDone: () -> Unit,
) {
    val c = Mind.colors
    LazyColumn(
        Modifier.fillMaxWidth().testTag("filingResults"),
        contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Caption("Session wrapped up")
                Text(
                    when {
                        state.filing -> "Filing your session…"
                        state.failed -> "Filing failed. I'll try again on next launch."
                        else -> LibraryUi.filingHeadline(state.filedFromSession, state.waitingFromSession)
                    },
                    style = Mind.type.screenTitle.copy(fontSize = Mind.type.screenTitle.fontSize.times(24f / 30f)), color = c.ink,
                )
                if (state.filing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = c.accent, trackColor = c.line)
            }
        }
        state.confirmations.forEach { (id, text) -> item(key = "ok$id") { Text(text, style = Mind.type.button, color = c.accent) } }
        if (state.needs.isNotEmpty()) item { SectionLabel("Needs you") }
        itemsIndexed(state.needs, key = { _, it -> "n${it.entry.id}" }) { i, item ->
            Appear(i) { NeedsYouCard(item, onOpenEntry, onAccept, onChange, onDiscard) }
        }
        if (state.filed.isNotEmpty()) {
            item { SectionLabel("Filed automatically", Modifier.padding(top = 4.dp)) }
            itemsIndexed(state.filed, key = { _, it -> "f${it.entry.id}" }) { i, f ->
                Appear(state.needs.size + i) { FiledCard(f, onOpenEntry, onOpenTopic, onUndo) }
            }
        }
        item { Row { Pill("Done", onDone, Modifier.testTag("closeResults"), style = PillStyle.INK, height = MindDimens.touch) } }
    }
}

@Composable
private fun FiledCard(f: FiledItem, onOpenEntry: (Long) -> Unit, onOpenTopic: (EntryEntity) -> Unit, onUndo: (EntryEntity) -> Unit) {
    val c = Mind.colors
    MindCard(onClick = { onOpenEntry(f.entry.id) }, padding = PaddingValues(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TypeChip(f.entry.entryType())
            Spacer(Modifier.weight(1f))
            if (f.undoable) Pill("Undo", { onUndo(f.entry) }, Modifier.testTag("undoFiling"), style = PillStyle.TEXT, height = MindDimens.touch)
        }
        Text(f.entry.title, style = Mind.type.body, color = c.ink)
        if (f.breadcrumb.isNotBlank()) {
            Text(
                f.breadcrumb, style = Mind.type.label.copy(fontWeight = FontWeight.Normal), color = c.accent,
                modifier = Modifier.padding(end = 8.dp).clickable { onOpenTopic(f.entry) },
            )
        }
    }
}

@Composable
private fun StatsRow(st: com.maheshreddy.mym.core.library.LibraryStats) {
    val c = Mind.colors
    Row(Modifier.fillMaxWidth().testTag("inboxStats"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            st.entriesThisWeek to if (st.entriesThisWeek == 1) "entry this week" else "entries this week",
            st.topics to if (st.topics == 1) "topic" else "topics",
            st.streakDays to if (st.streakDays == 1) "day streak" else "day streak",
        ).forEach { (n, label) ->
            Column(
                Modifier.weight(1f).background(c.surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("$n", style = Mind.type.cardTitle, color = c.ink)
                Caption(label)
            }
        }
    }
}

/** design-spec §4 Wrap-up and Inbox: "Needs you" cards first, then what was filed automatically (with Undo). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxContent(
    state: InboxUiState,
    snackbar: SnackbarHostState,
    onBack: (() -> Unit)?,
    onOpenEntry: (Long) -> Unit,
    onOpenTopic: (EntryEntity) -> Unit,
    onAccept: (EntryEntity) -> Unit,
    onChange: (EntryEntity) -> Unit,
    onDiscard: (EntryEntity) -> Unit,
    onUndo: (EntryEntity) -> Unit,
    onWrapUpSession: (Long) -> Unit = {},
    onOpenSession: (Long) -> Unit = {},
) {
    val c = Mind.colors
    Scaffold(
        topBar = { if (onBack != null) BackBar(onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = c.ground,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).then(if (onBack == null) Modifier.statusBarsPadding() else Modifier).testTag("inbox"),
            contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, top = if (onBack == null) 28.dp else 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Caption(if (state.sessionMode) "Session wrapped up" else "Inbox")
                    Text(
                        when {
                            state.sessionMode && state.filing -> "Filing your session…"
                            state.sessionMode && state.failed -> "Filing failed. I'll try again on next launch."
                            state.sessionMode -> LibraryUi.filingHeadline(state.filedFromSession, state.waitingFromSession)
                            state.needs.isEmpty() -> "Nothing needs your call"
                            state.needs.size == 1 -> "One entry needs your call."
                            else -> "${LibraryUi.word(state.needs.size).replaceFirstChar { it.uppercase() }} entries need your call."
                        },
                        style = Mind.type.screenTitle.copy(fontSize = Mind.type.screenTitle.fontSize.times(28f / 30f)), color = c.ink,
                    )
                    if (state.sessionMode && state.filing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = c.accent, trackColor = c.line)
                }
            }
            state.stale.forEach { s ->
                item(key = "stale${s.id}") {
                    MindCard(border = BorderStroke(1.5.dp, c.amberBorder), modifier = Modifier.testTag("staleSession")) {
                        Text("\"${s.title}\" is still open. Wrap it up so what you said gets filed?", style = Mind.type.body, color = c.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Wrap up", { onWrapUpSession(s.id) })
                            Pill("Open it", { onOpenSession(s.id) }, style = PillStyle.OUTLINE)
                        }
                    }
                }
            }
            state.confirmations.forEach { (id, text) ->
                item(key = "ok$id") { Text(text, style = Mind.type.button, color = c.accent) }
            }
            itemsIndexed(state.needs, key = { _, it -> "n${it.entry.id}" }) { i, item ->
                Appear(i) { NeedsYouCard(item, onOpenEntry, onAccept, onChange, onDiscard) }
            }
            if (!state.sessionMode && state.needs.isEmpty() && state.stale.isEmpty()) {
                state.stats?.let { st -> item { StatsRow(st) } }
                if (state.recentFiled.isNotEmpty()) {
                    item { SectionLabel("Last filed", Modifier.padding(top = 4.dp)) }
                    itemsIndexed(state.recentFiled, key = { _, it -> "r${it.entry.id}" }) { i, f ->
                        Appear(i) { FiledCard(f, onOpenEntry, onOpenTopic, onUndo) }
                    }
                }
            } else if (state.filed.isNotEmpty()) {
                item { SectionLabel(if (state.sessionMode) "Filed automatically" else "Filed today", Modifier.padding(top = 4.dp)) }
                itemsIndexed(state.filed, key = { _, it -> "f${it.entry.id}" }) { i, f ->
                    Appear(state.needs.size + i) {
                        FiledCard(f, onOpenEntry, onOpenTopic, onUndo)
                    }
                }
            }
        }
    }
}

/** Filing results animate in one by one (≤ 200 ms each, design-spec §5); system "remove animations" makes this instant. */
@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(180, delayMillis = (index * 60).coerceAtMost(600))) + slideInVertically(tween(180, delayMillis = (index * 60).coerceAtMost(600))) { it / 6 },
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NeedsYouCard(
    item: InboxItem,
    onOpenEntry: (Long) -> Unit,
    onAccept: (EntryEntity) -> Unit,
    onChange: (EntryEntity) -> Unit,
    onDiscard: (EntryEntity) -> Unit,
) {
    val c = Mind.colors
    val e = item.entry
    MindCard(onClick = { onOpenEntry(e.id) }, border = BorderStroke(1.5.dp, c.amberBorder)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NeedsYouChip(); TypeChip(e.entryType()); PrivacyChip(e.entryPrivacy())
        }
        Text(e.title, style = Mind.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
        Text(e.body.lines().joinToString(" ") { it.removePrefix("- ").trim() }, style = Mind.type.body, color = c.ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(
            buildAnnotatedString {
                if (item.subject == null && item.topic == null) {
                    append("I'm unsure where this goes. Choose a subject and topic.")
                } else {
                    append("I'm unsure where this goes. Suggestion: ")
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) {
                        append(listOfNotNull(item.subject ?: "?", item.topic ?: "?").joinToString(" › "))
                    }
                    when {
                        item.newSubject && item.newTopic -> append(" (new subject and topic)")
                        item.newTopic -> append(" (new topic)")
                        item.newSubject -> append(" (new subject)")
                    }
                }
            },
            style = Mind.type.label.copy(fontWeight = FontWeight.Normal), color = c.muted,
        )
        if (e.reason.isNotBlank()) Caption(e.reason)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.canAccept) Pill("File", { onAccept(e) }, Modifier.testTag("accept"))
            Pill(if (item.canAccept) "Change" else "Choose", { onChange(e) }, style = if (item.canAccept) PillStyle.OUTLINE else PillStyle.ACCENT)
            Box { Pill("Discard", { onDiscard(e) }, Modifier.testTag("discard"), style = PillStyle.TEXT) }
        }
    }
}

@Composable
internal fun rememberMessages(vm: LibraryBaseViewModel): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    return snackbar
}
