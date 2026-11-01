package com.freshernotes.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.search.DocKind
import com.freshernotes.app.search.SearchHit
import com.freshernotes.app.ui.ConfirmDialog
import com.freshernotes.app.ui.TextPromptDialog
import com.freshernotes.app.ui.components.BUTLER_NAME
import com.freshernotes.app.ui.components.BackBar
import com.freshernotes.app.ui.components.BookSpine
import com.freshernotes.app.ui.components.Caption
import com.freshernotes.app.ui.components.DashedCard
import com.freshernotes.app.ui.components.Pill
import com.freshernotes.app.ui.components.PillStyle
import com.freshernotes.app.ui.components.ScreenTitle
import com.freshernotes.app.ui.theme.Mind
import com.freshernotes.app.ui.theme.MindDimens
import com.freshernotes.core.library.Privacy

data class LibraryUiState(
    val topicCount: Int = 0,
    val entryCount: Int = 0,
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val filter: Privacy? = null,
    val shelves: List<Shelf> = emptyList(),
    val unassigned: List<TopicTile> = emptyList(),
)

@Composable
fun LibraryScreen(
    onOpenSubject: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onTalk: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(vm)
    if (creating) TextPromptDialog("New subject", "", "Create", { vm.createSubject(it); creating = false }, { creating = false })
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Mind.colors.ground) { padding ->
        Box(Modifier.padding(padding)) {
            LibraryContent(
                state, onQuery = vm::setQuery, onFilter = vm::setFilter, onOpenSubject = onOpenSubject, onOpenTopic = onOpenTopic,
                onOpenHit = { hit ->
                    when (hit.kind) {
                        DocKind.TOPIC -> onOpenTopic(hit.refId)
                        DocKind.ENTRY -> onOpenEntry(hit.refId)
                        DocKind.SESSION -> onOpenSession(hit.refId)
                    }
                },
                onTalk = onTalk, onNewSubject = { creating = true },
            )
        }
    }
}

/** design-spec §4 Library: totals, search (FTS5), privacy filter, subjects as shelves of topic spines. */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQuery: (String) -> Unit,
    onFilter: (Privacy?) -> Unit,
    onOpenSubject: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onTalk: () -> Unit,
    onNewSubject: () -> Unit,
) {
    val c = Mind.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.ground).statusBarsPadding().testTag("library"),
        contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                ScreenTitle("Library", Modifier.weight(1f))
                Caption("${LibraryUi.plural(state.topicCount, "topic", "topics")} · ${LibraryUi.plural(state.entryCount, "entry", "entries")}")
            }
        }
        item { SearchField(state.query, onQuery) }
        if (state.query.isNotBlank()) {
            if (state.results.isEmpty()) item { Caption("Nothing matches \"${state.query.trim()}\".") }
            items(state.results, key = { "r${it.kind}${it.refId}" }) { hit ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onOpenHit(hit) }.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        when (hit.kind) { DocKind.TOPIC -> "TOPIC"; DocKind.ENTRY -> "ENTRY"; DocKind.SESSION -> "SESSION" },
                        style = Mind.type.overline, color = c.muted,
                    )
                    Text(hit.title, style = Mind.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
                    Text(hit.snippet.replace("**", ""), style = Mind.type.caption, color = c.muted, maxLines = 2)
                }
            }
            return@LazyColumn
        }
        item { PrivacyFilter(state.filter, onFilter) }
        if (state.shelves.isEmpty() && state.unassigned.isEmpty()) item {
            Caption(if (state.filter != null) "No topics have ${state.filter.label} entries." else "No subjects yet. They appear when you confirm where $BUTLER_NAME files things.")
        }
        // Spine colours rotate through the palette in display order, so neighbours differ.
        val offsets = state.shelves.runningFold(0) { acc, s -> acc + s.topics.size }
        state.shelves.forEachIndexed { i, shelf ->
            item(key = "s${shelf.subjectId}") {
                ShelfSection(shelf.title, shelf.topics, onHeader = { onOpenSubject(shelf.subjectId) }, onOpenTopic = onOpenTopic, emptyAction = onTalk, colorOffset = offsets[i])
            }
        }
        if (state.unassigned.isNotEmpty()) item {
            ShelfSection("Needs a subject", state.unassigned, onHeader = null, onOpenTopic = onOpenTopic, emptyAction = onTalk, colorOffset = offsets.last())
        }
        if (state.filter == null) item { Pill("+ New subject", onNewSubject, Modifier.fillMaxWidth(), style = PillStyle.OUTLINE) }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Mind.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption("Search everything you've said")
        Box(
            Modifier.fillMaxWidth().heightIn(min = MindDimens.touch).clip(RoundedCornerShape(12.dp)).background(c.surface)
                .border(1.dp, c.fieldLine, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (query.isEmpty()) Text("e.g. kickoff, datum, ECO", style = Mind.type.body, color = c.muted)
            BasicTextField(
                query, onQuery, singleLine = true, textStyle = Mind.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().testTag("search"),
            )
        }
    }
}

@Composable
private fun PrivacyFilter(selected: Privacy?, onFilter: (Privacy?) -> Unit) {
    val c = Mind.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<Privacy?>(null) + Privacy.entries).forEach { p ->
            val on = p == selected
            Box(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) c.ink else c.surface)
                    .then(if (on) Modifier else Modifier.border(1.dp, c.fieldLine, RoundedCornerShape(18.dp)))
                    .clickable(role = Role.RadioButton) { onFilter(p) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(p?.label ?: "All", style = if (on) Mind.type.button.copy(fontSize = Mind.type.label.fontSize) else Mind.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) c.onInk else c.ink)
            }
        }
    }
}

@Composable
private fun ShelfSection(title: String, topics: List<TopicTile>, onHeader: (() -> Unit)?, onOpenTopic: (Long) -> Unit, emptyAction: () -> Unit, colorOffset: Int = 0) {
    val c = Mind.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).then(if (onHeader != null) Modifier.clickable(role = Role.Button, onClick = onHeader) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Mind.type.label.copy(fontSize = Mind.type.body.fontSize.times(16f / 15f)), color = c.ink, modifier = Modifier.weight(1f))
            if (topics.isNotEmpty()) Caption(LibraryUi.plural(topics.size, "topic", "topics"))
        }
        if (topics.isEmpty()) {
            DashedCard("Empty shelf. Teach $BUTLER_NAME about $title?", "Talk", emptyAction)
        } else {
            topics.chunked(2).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { k, t ->
                        BookSpine(t.title, LibraryUi.caption(t), c.spines[(colorOffset + r * 2 + k) % c.spines.size], onClick = { onOpenTopic(t.id) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- Subject (pushed from a shelf heading): rename, merge, delete ----

@Composable
fun SubjectScreen(onBack: () -> Unit, onOpenTopic: (Long) -> Unit, onTalk: () -> Unit, vm: SubjectViewModel = hiltViewModel()) {
    val subject by vm.subject.collectAsStateWithLifecycle()
    val topics by vm.topics.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(vm)
    val c = Mind.colors

    if (renaming) TextPromptDialog("Rename subject", subject?.title.orEmpty(), "Rename", { vm.rename(it); renaming = false }, { renaming = false })
    if (merging) PickOneDialog(
        "Merge into…", subjects.filter { it.id != vm.subjectId }.map { it.id to it.title },
        onPick = { vm.mergeInto(it, onBack); merging = false }, onDismiss = { merging = false },
    )
    if (deleting) ConfirmDialog(
        "Delete subject?", "Its topics and entries are kept and listed under \"Needs a subject\".", "Delete",
        { deleting = false; vm.delete(onBack) }, { deleting = false },
    )
    Scaffold(
        topBar = {
            BackBar(onBack) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename", tint = c.ink) }
                IconButton(onClick = { merging = true }) { Icon(Icons.AutoMirrored.Filled.MergeType, "Merge", tint = c.ink) }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, "Delete", tint = c.ink) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = c.ground,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(MindDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenTitle(subject?.title ?: "") }
            item { ShelfSection("Topics", topics.mapNotNull { t -> tiles.firstOrNull { it.id == t.id } }, null, onOpenTopic, onTalk) }
        }
    }
}

/** Choose one item from a short list (merge targets). */
@Composable
internal fun PickOneDialog(title: String, options: List<Pair<Long, String>>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                if (options.isEmpty()) item { Text("Nothing to merge into.") }
                items(options, key = { it.first }) { (id, label) ->
                    ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { onPick(id) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
