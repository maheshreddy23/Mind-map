package com.maheshreddy.mym.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.data.Pick
import com.maheshreddy.mym.ui.components.BackBar
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.PrivacyChip
import com.maheshreddy.mym.ui.components.TypeChip
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.components.NeedsYouChip
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens
import com.maheshreddy.mym.ui.ConfirmDialog
import com.maheshreddy.mym.ui.formatTime
import com.maheshreddy.mym.ui.notes.MarkdownView
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy

/** One entry on its own: read, edit (title, text, type, privacy), move or split out, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    vm: EntryViewModel = hiltViewModel(),
) {
    val entry by vm.entry.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.allTopics.collectAsStateWithLifecycle()
    val snackbar = rememberMessages(vm)
    var editing by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(EntryType.KNOWLEDGE) }
    var privacy by rememberSaveable { mutableStateOf(Privacy.DEFAULT) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val e = entry

    if (moving && e != null) {
        val topic = topics.firstOrNull { it.id == e.topicId }
        FileEntryDialog(
            title = if (e.status == "INBOX") "File entry" else "Move entry (pick \"New topic\" to split it out)",
            subjects = subjects, topics = topics,
            initialSubject = topic?.subjectId?.let { Pick.Existing(it) } ?: e.proposedSubjectPick(),
            initialTopic = topic?.let { Pick.Existing(it.id) } ?: e.proposedTopicPick(),
            initialType = e.entryType(), initialPrivacy = e.entryPrivacy(),
            confirmLabel = if (e.status == "INBOX") "File" else "Move",
            onConfirm = { vm.move(it); moving = false }, onDismiss = { moving = false },
        )
    }
    if (deleting) ConfirmDialog("Delete entry?", "This entry is removed from the library. Its session transcript stays.", "Delete", { deleting = false; vm.delete(onBack) }, { deleting = false })

    Scaffold(
        topBar = {
            BackBar(onBack) {
                if (editing) {
                    IconButton(onClick = { vm.save(title, body, type, privacy); editing = false }) { Icon(Icons.Default.Check, "Save") }
                } else if (e != null) {
                    IconButton(onClick = {
                        title = e.title; body = e.body; type = e.entryType(); privacy = e.entryPrivacy(); editing = true
                    }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { moving = true }) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mind.colors.ground,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MindDimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (e == null) return@Column
            if (editing) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(top = 8.dp), label = { Text("Text") })
                Text("Type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntryType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                }
                Text("Privacy", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Privacy.entries.forEach { p -> FilterChip(selected = privacy == p, onClick = { privacy = p }, label = { Text(p.label) }) }
                }
            } else {
                val topic = topics.firstOrNull { it.id == e.topicId }
                val subject = subjects.firstOrNull { it.id == topic?.subjectId }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (e.status == "INBOX") NeedsYouChip()
                    TypeChip(e.entryType()); PrivacyChip(e.entryPrivacy())
                }
                ScreenTitle(e.title)
                Caption(formatTime(e.createdAt))
                if (topic != null) {
                    TextButton(onClick = { onOpenTopic(topic.id) }) { Text(listOfNotNull(subject?.title, topic.title).joinToString(" › "), style = Mind.type.label, color = Mind.colors.accent) }
                }
                MarkdownView(e.body, Modifier.padding(top = 4.dp))
                e.sessionId?.let { sid -> TextButton(onClick = { onOpenSession(sid) }) { Text("Open the session it came from") } }
            }
        }
    }
}
