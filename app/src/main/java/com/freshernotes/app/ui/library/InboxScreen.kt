package com.freshernotes.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.data.Pick
import com.freshernotes.app.data.db.EntryEntity
import com.freshernotes.app.ui.BackTopBar

/** v2 §6.3 / §11.3: entries awaiting confirmation, plus today's automatic filings with Undo. */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenEntry: (Long) -> Unit, vm: InboxViewModel = hiltViewModel()) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val filedToday by vm.filedToday.collectAsStateWithLifecycle()
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
    Scaffold(topBar = { BackTopBar("Inbox", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding).testTag("inbox")) {
            if (inbox.isEmpty()) item { Hint("Nothing waiting. Entries the butler is unsure about appear here.") }
            items(inbox, key = { "i${it.id}" }) { e ->
                val subjectName = (e.proposedSubjectPick() as? Pick.Existing)?.let { p -> subjects.firstOrNull { it.id == p.id }?.title } ?: e.proposedSubject
                val topicName = (e.proposedTopicPick() as? Pick.Existing)?.let { p -> topics.firstOrNull { it.id == p.id }?.title } ?: e.proposedTopic
                InboxCard(
                    e, subjectName, topicName,
                    newSubject = e.proposedSubjectPick() is Pick.New, newTopic = e.proposedTopicPick() is Pick.New,
                    canAccept = e.proposedSubjectPick() != null && e.proposedTopicPick() != null,
                    onOpen = { onOpenEntry(e.id) }, onAccept = { vm.accept(e) }, onChange = { filing = e }, onReject = { vm.reject(e) },
                )
            }
            if (filedToday.isNotEmpty()) {
                item { SectionTitle("Filed today") }
                items(filedToday, key = { "f${it.id}" }) { e ->
                    val topic = topics.firstOrNull { it.id == e.topicId }
                    Row(Modifier.fillMaxWidth().clickable { onOpenEntry(e.id) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.entryType().label} · ${topic?.title ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.undo(e) }) { Text("Undo") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxCard(
    e: EntryEntity,
    subject: String?,
    topic: String?,
    newSubject: Boolean,
    newTopic: Boolean,
    canAccept: Boolean,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onChange: () -> Unit,
    onReject: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium)
            Text("${e.entryType().label} · ${e.entryPrivacy().label}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.padding(2.dp))
            Text(e.body, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.padding(2.dp))
            Text(
                "Proposed: " + (subject?.let { it + if (newSubject) " (new)" else "" } ?: "no subject") +
                    " › " + (topic?.let { it + if (newTopic) " (new)" else "" } ?: "no topic"),
                style = MaterialTheme.typography.bodySmall,
            )
            if (e.reason.isNotBlank()) Text(e.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onAccept, enabled = canAccept) { Text("Accept") }
                TextButton(onClick = onChange) { Text("Change") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReject) { Text("Reject") }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))

@Composable
internal fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
internal fun rememberMessages(vm: LibraryBaseViewModel): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    return snackbar
}
