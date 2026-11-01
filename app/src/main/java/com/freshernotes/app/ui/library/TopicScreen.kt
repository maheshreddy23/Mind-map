package com.freshernotes.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.data.Pick
import com.freshernotes.app.ui.BackTopBar
import com.freshernotes.app.ui.ConfirmDialog
import com.freshernotes.app.ui.TextPromptDialog
import com.freshernotes.app.ui.notes.MarkdownView
import com.freshernotes.core.library.EntryType
import com.freshernotes.core.library.Privacy

/** v2 §6.4: the topic page (rebuilt from entries), every entry on its own, and topic maintenance. */
@Composable
fun TopicScreen(onBack: () -> Unit, onOpenEntry: (Long) -> Unit, vm: TopicViewModel = hiltViewModel()) {
    val topic by vm.topic.collectAsStateWithLifecycle()
    val subject by vm.subject.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.allTopics.collectAsStateWithLifecycle()
    val summarizing by vm.summarizing.collectAsStateWithLifecycle()
    val snackbar = rememberMessages(vm)
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var showLegacy by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            BackTopBar(topic?.title ?: "Topic", onBack) {
                IconButton(onClick = vm::export) { Icon(Icons.Default.Share, "Export Markdown") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        "Rewrite summary" to { vm.rewriteSummary() },
                        "Rename" to { dialog = "rename" },
                        (if (subject == null) "Choose subject" else "Move to subject") to { dialog = "move" },
                        "Merge into…" to { dialog = "merge" },
                        "Delete topic" to { dialog = "delete" },
                        "Delete topic and entries" to { dialog = "deleteAll" },
                    ).forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() }) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("topic")) {
            if (summarizing) {
                Text("The butler is rewriting the summary…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (subject == null && topic != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        Text("This topic has no subject yet.", Modifier.weight(1f))
                        TextButton(onClick = { dialog = "move" }) { Text("Choose") }
                    }
                }
            }
            MarkdownView(topic?.markdown.orEmpty())
            if (topic?.summary?.isNotBlank() == true) TextButton(onClick = vm::clearSummary) { Text("Remove summary") }

            if (entries.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Entries", style = MaterialTheme.typography.titleMedium)
                entries.forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenEntry(e.id) }.padding(vertical = 8.dp)) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${e.entryType().label} · ${e.entryPrivacy().label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val legacy = topic?.legacyMarkdown.orEmpty()
            if (legacy.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row {
                    TextButton(onClick = { showLegacy = !showLegacy }) { Text(if (showLegacy) "Hide earlier page (v1)" else "Show earlier page (v1)") }
                    TextButton(onClick = vm::discardLegacy) { Text("Discard it") }
                }
                Text(
                    "Kept exactly as it was before the update, including any edits you made. Discard it once the entries above cover it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (showLegacy) MarkdownView(legacy, Modifier.padding(top = 8.dp))
            }
        }
    }
}
