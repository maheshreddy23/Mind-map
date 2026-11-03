package com.maheshreddy.mym.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.data.Pick
import com.maheshreddy.mym.data.db.EntryEntity
import com.maheshreddy.mym.ui.ConfirmDialog
import com.maheshreddy.mym.ui.TextPromptDialog
import com.maheshreddy.mym.ui.components.BUTLER_NAME
import com.maheshreddy.mym.ui.components.BackBar
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.MindCard
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.PillStyle
import com.maheshreddy.mym.ui.components.PrivacyChip
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.components.TypeChip
import com.maheshreddy.mym.ui.formatTime
import com.maheshreddy.mym.ui.notes.MarkdownView
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy

data class TopicUiState(
    val title: String = "",
    val subject: String? = null,
    val hasSubject: Boolean = true,
    val summary: String = "",
    val updatedAt: Long = 0,
    val entries: List<EntryEntity> = emptyList(),
    val summarizing: Boolean = false,
)

@Composable
fun TopicScreen(onBack: () -> Unit, onOpenEntry: (Long) -> Unit, onContinue: (String) -> Unit, onAsk: (String) -> Unit = {}, vm: TopicViewModel = hiltViewModel()) {
    val topic by vm.topic.collectAsStateWithLifecycle()
    val subject by vm.subject.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.allTopics.collectAsStateWithLifecycle()
    val summarizing by vm.summarizing.collectAsStateWithLifecycle()
    val snackbar = rememberMessages(vm)
    var dialog by remember { mutableStateOf<String?>(null) }

    when (dialog) {
        "rename" -> TextPromptDialog("Rename topic", topic?.title.orEmpty(), "Rename", { vm.rename(it); dialog = null }, { dialog = null })
        "move" -> FileEntryDialog(
            title = "Move topic to subject",
            subjects = subjects, topics = emptyList(),
            initialSubject = subject?.let { Pick.Existing(it.id) }, initialTopic = Pick.Existing(vm.topicId),
            initialType = EntryType.KNOWLEDGE, initialPrivacy = Privacy.DEFAULT, showTypeAndPrivacy = false, confirmLabel = "Move",
            onConfirm = { vm.moveTo(it.subject); dialog = null }, onDismiss = { dialog = null },
        )
        "merge" -> PickOneDialog(
            "Merge this topic into…",
            topics.filter { it.id != vm.topicId }.map { t -> t.id to (t.title + (subjects.firstOrNull { it.id == t.subjectId }?.let { " · ${it.title}" } ?: "")) },
            onPick = { vm.mergeInto(it, onBack); dialog = null }, onDismiss = { dialog = null },
        )
        "delete" -> ConfirmDialog(
            "Delete topic?", "Its ${entries.size} entries go back to the Inbox so nothing is lost.", "Delete",
            { dialog = null; vm.delete(deleteEntries = false, onDone = onBack) }, { dialog = null },
        )
        "deleteAll" -> ConfirmDialog(
            "Delete topic and its entries?", "The topic and its ${entries.size} entries are deleted for good. Sessions stay.", "Delete all",
            { dialog = null; vm.delete(deleteEntries = true, onDone = onBack) }, { dialog = null },
        )
    }

    TopicContent(
        state = TopicUiState(
            title = topic?.title.orEmpty(), subject = subject?.title, hasSubject = topic == null || topic?.subjectId != null,
            summary = topic?.summary.orEmpty(), updatedAt = topic?.updatedAt ?: 0, entries = entries,
            summarizing = summarizing,
        ),
        snackbar = snackbar,
        onBack = onBack,
        onOpenEntry = onOpenEntry,
        onMenu = { action ->
            when (action) {
                "summary" -> vm.rewriteSummary()
                "clearSummary" -> vm.clearSummary()
                "export" -> vm.export()
                else -> dialog = action
            }
        },
        onContinue = { topic?.title?.let(onContinue) },
        onAsk = { topic?.title?.let(onAsk) },
    )
}

/** design-spec §4 Topic page. */
@Composable
fun TopicContent(
    state: TopicUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onMenu: (String) -> Unit,
    onContinue: () -> Unit,
    initialTab: Int = 0,
    onAsk: () -> Unit = {},
) {
    val c = Mind.colors
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = c.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BackBar(onBack) {
                IconButton(onClick = { onMenu("export") }) { Icon(Icons.Default.Share, "Export Markdown", tint = c.ink) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Topic options", tint = c.ink) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOfNotNull(
                            "Rewrite summary" to "summary",
                            if (state.summary.isNotBlank()) "Remove summary" to "clearSummary" else null,
                            "Rename" to "rename",
                            (if (state.hasSubject) "Move to subject" else "Choose subject") to "move",
                            "Merge into…" to "merge",
                            "Delete topic" to "delete",
                            "Delete topic and entries" to "deleteAll",
                        ).forEach { (label, key) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onMenu(key) }) }
                    }
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(c.ground).drawBehind {
                    drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }.navigationBarsPadding().padding(horizontal = MindDimens.screenPadding, vertical = 12.dp),
            ) {
                Pill("Ask about this", onAsk, Modifier.weight(1f).testTag("askAboutTopic"), style = PillStyle.OUTLINE, height = 48.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Pill("Continue topic", onContinue, Modifier.weight(1.3f).testTag("continueTopic"), height = 48.dp, leading = Icons.Default.Mic)
            }
        },
    ) { padding ->
        val sessions = state.entries.mapNotNull { it.sessionId }.distinct().size
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("topic"),
            contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Caption(state.subject ?: "No subject yet")
                    ScreenTitle(state.title)
                    Caption(
                        listOfNotNull(
                            if (sessions > 0) "From " + LibraryUi.plural(sessions, "session", "sessions") else null,
                            if (state.summary.isNotBlank()) "summary by $BUTLER_NAME" else null,
                            if (state.updatedAt > 0) "updated " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(state.updatedAt)) else null,
                        ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                    )
                }
            }
            if (!state.hasSubject) item {
                MindCard(background = c.amberSoft, border = null, onClick = { onMenu("move") }) {
                    Text("This topic has no subject yet. Tap to choose one.", style = Mind.type.body, color = c.ink)
                }
            }
            if (state.entries.isEmpty() && state.title.isNotEmpty()) item {
                MindCard(background = c.amberSoft, border = null, modifier = Modifier.testTag("emptyTopic")) {
                    Text("This topic has no entries. Continue it, or delete it if it was left behind.", style = Mind.type.body, color = c.ink)
                    Pill("Delete topic", { onMenu("delete") }, style = PillStyle.INK, height = MindDimens.touch)
                }
            }
            item { Tabs(tab, state.entries.size) { tab = it } }
            if (state.summarizing) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Caption("$BUTLER_NAME is rewriting the summary…")
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
                }
            }
            if (tab == 0) {
                summaryItems(state, onOpenEntry, onMenu)
            } else {
                if (state.entries.isEmpty()) item { Caption("No entries yet.") }
                items(state.entries, key = { it.id }) { e ->
                    MindCard(onClick = { onOpenEntry(e.id) }, padding = PaddingValues(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TypeChip(e.entryType()); PrivacyChip(e.entryPrivacy()) }
                        Text(e.title, style = Mind.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
                        Text(e.body.lines().joinToString(" ") { it.removePrefix("- ").trim() }, style = Mind.type.body, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Caption((if (e.sessionId != null) "Session " else "Added ") + formatTime(e.createdAt))
                    }
                }
            }
        }
    }
}

@Composable
private fun Tabs(selected: Int, count: Int, onSelect: (Int) -> Unit) {
    val c = Mind.colors
    Row(Modifier.fillMaxWidth().drawBehind { drawLine(c.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }) {
        listOf("Summary", "Entries · $count").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.heightIn(min = MindDimens.touch).clickable(role = Role.Tab) { onSelect(i) }
                    .drawBehind { if (on) drawLine(c.accent, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(label, style = if (on) Mind.type.button else Mind.type.button.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) c.ink else c.muted)
            }
        }
    }
}

/** Summary: M's overview, the knowledge as numbered steps, then lessons/principles, ideas, and open questions. */
private fun androidx.compose.foundation.lazy.LazyListScope.summaryItems(state: TopicUiState, onOpenEntry: (Long) -> Unit, onMenu: (String) -> Unit) {
    val byType = state.entries.groupBy { it.entryType() }
    item {
        if (state.summary.isNotBlank()) {
            Text(state.summary, style = Mind.type.reading, color = Mind.colors.ink)
        } else if (state.entries.isNotEmpty()) {
            Row(Modifier.clip(RoundedCornerShape(12.dp))) { Pill("Ask $BUTLER_NAME to write a summary", { onMenu("summary") }, style = PillStyle.OUTLINE, height = 40.dp) }
        } else {
            Caption("No entries yet. Continue the topic to add some.")
        }
    }
    byType[EntryType.KNOWLEDGE]?.let { list ->
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEachIndexed { i, e ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenEntry(e.id) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}.", style = Mind.type.body, color = Mind.colors.muted)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(e.title, style = Mind.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Mind.colors.ink)
                            MarkdownView(e.body)
                        }
                    }
                }
            }
        }
    }
    val boxes = listOf(
        Triple(EntryType.LESSON, "Lessons", 0), Triple(EntryType.PRINCIPLE, "Principles", 0),
        Triple(EntryType.IDEA, "Ideas", 1), Triple(EntryType.REFLECTION, "Reflections", 1), Triple(EntryType.OPEN_QUESTION, "Open questions", 2),
    )
    boxes.forEach { (type, label, style) ->
        byType[type]?.let { list ->
            item(key = "box$type") {
                val c = Mind.colors
                val (bg, fg) = when (style) {
                    0 -> c.accentSoft to c.accent
                    2 -> c.amberTint to c.amberText
                    else -> c.neutralChip to c.neutralChipText
                }
                SummaryBox(label, bg, fg, list, onOpenEntry)
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, background: Color, labelColor: Color, entries: List<EntryEntity>, onOpenEntry: (Long) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = Mind.type.label, color = labelColor)
        entries.forEach { e ->
            Column(Modifier.fillMaxWidth().clickable { onOpenEntry(e.id) }) {
                Text(e.title, style = Mind.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Mind.colors.ink)
                MarkdownView(e.body)
            }
        }
    }
}
