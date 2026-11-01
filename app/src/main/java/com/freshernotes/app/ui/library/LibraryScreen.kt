package com.freshernotes.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.MergeType
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.ui.BackTopBar
import com.freshernotes.app.ui.ConfirmDialog
import com.freshernotes.app.ui.TextPromptDialog

/** v2 §11.4: subjects → topics. v1 topics the migration could not place are listed under "Needs a subject". */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenSubject: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenInbox: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.allTopics.collectAsStateWithLifecycle()
    val unassigned by vm.unassigned.collectAsStateWithLifecycle()
    val inboxCount by vm.inboxCount.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(vm)
    if (creating) TextPromptDialog("New subject", "", "Create", { vm.createSubject(it); creating = false }, { creating = false })

    Scaffold(
        topBar = {
            BackTopBar("Library", onBack) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, contentDescription = "New subject") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).testTag("library")) {
            if (inboxCount > 0) item {
                ListItem(
                    headlineContent = { Text("Inbox") },
                    supportingContent = { Text("$inboxCount waiting for confirmation") },
                    modifier = Modifier.clickable(onClick = onOpenInbox),
                )
            }
            item { SectionTitle("Subjects") }
            if (subjects.isEmpty()) item { Hint("No subjects yet. They are created when you confirm the butler's filing.") }
            items(subjects, key = { "s${it.id}" }) { s ->
                val count = topics.count { it.subjectId == s.id }
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(if (count == 1) "1 topic" else "$count topics") },
                    modifier = Modifier.clickable { onOpenSubject(s.id) },
                )
            }
            if (unassigned.isNotEmpty()) {
                item { SectionTitle("Needs a subject") }
                items(unassigned, key = { "u${it.id}" }) { t ->
                    ListItem(headlineContent = { Text(t.title) }, modifier = Modifier.clickable { onOpenTopic(t.id) })
                }
            }
        }
    }
}

@Composable
fun SubjectScreen(onBack: () -> Unit, onOpenTopic: (Long) -> Unit, vm: SubjectViewModel = hiltViewModel()) {
    val subject by vm.subject.collectAsStateWithLifecycle()
    val topics by vm.topics.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(vm)

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
            BackTopBar(subject?.title ?: "Subject", onBack) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                IconButton(onClick = { merging = true }) { Icon(Icons.AutoMirrored.Filled.MergeType, "Merge") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, "Delete") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (topics.isEmpty()) item { Hint("No topics yet.") }
            items(topics, key = { it.id }) { t ->
                ListItem(headlineContent = { Text(t.title) }, modifier = Modifier.clickable { onOpenTopic(t.id) })
            }
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
