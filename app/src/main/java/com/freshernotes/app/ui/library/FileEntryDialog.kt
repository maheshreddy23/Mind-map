package com.freshernotes.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshernotes.app.data.Pick
import com.freshernotes.app.data.db.EntryEntity
import com.freshernotes.app.data.db.SubjectEntity
import com.freshernotes.app.data.db.TopicEntity
import com.freshernotes.core.library.CatchAll
import com.freshernotes.core.library.EntryType
import com.freshernotes.core.library.Privacy

data class FilingChoice(val subject: Pick, val topic: Pick, val type: EntryType, val privacy: Privacy)

/** The Inbox proposal of an entry as picks, or null where the proposal is missing. */
fun EntryEntity.proposedSubjectPick(): Pick? =
    proposedSubjectId?.let { Pick.Existing(it) } ?: CatchAll.clean(proposedSubject)?.let { Pick.New(it) }

fun EntryEntity.proposedTopicPick(): Pick? =
    proposedTopicId?.let { Pick.Existing(it) } ?: CatchAll.clean(proposedTopic)?.let { Pick.New(it) }

fun EntryEntity.entryType(): EntryType = runCatching { EntryType.valueOf(type) }.getOrDefault(EntryType.KNOWLEDGE)
fun EntryEntity.entryPrivacy(): Privacy = runCatching { Privacy.valueOf(privacy) }.getOrDefault(Privacy.DEFAULT)

private const val NEW = -1L

/**
 * Pick a subject and topic (existing, or a new name), plus type and privacy. Used to file from the Inbox and to
 * move or split entries. A v1 topic without a subject can be picked; it then joins the chosen subject.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileEntryDialog(
    title: String,
    subjects: List<SubjectEntity>,
    topics: List<TopicEntity>,
    initialSubject: Pick?,
    initialTopic: Pick?,
    initialType: EntryType,
    initialPrivacy: Privacy,
    showTypeAndPrivacy: Boolean = true,
    confirmLabel: String = "File",
    onConfirm: (FilingChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val topicSubject = (initialTopic as? Pick.Existing)?.let { p -> topics.firstOrNull { it.id == p.id }?.subjectId }
    var subjectId by rememberSaveable {
        mutableStateOf(
            when (initialSubject) {
                is Pick.Existing -> initialSubject.id
                is Pick.New -> NEW
                null -> topicSubject ?: subjects.firstOrNull()?.id ?: NEW
            },
        )
    }
    var newSubject by rememberSaveable { mutableStateOf((initialSubject as? Pick.New)?.name ?: "") }
    var topicId by rememberSaveable { mutableStateOf((initialTopic as? Pick.Existing)?.id ?: NEW) }
    var newTopic by rememberSaveable { mutableStateOf((initialTopic as? Pick.New)?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var privacy by rememberSaveable { mutableStateOf(initialPrivacy) }

    val subjectPick: Pick? = if (subjectId == NEW) newSubject.trim().takeIf { it.isNotEmpty() }?.let { Pick.New(it) } else Pick.Existing(subjectId)
    val topicPick: Pick? = if (topicId == NEW) newTopic.trim().takeIf { it.isNotEmpty() }?.let { Pick.New(it) } else Pick.Existing(topicId)
    val visibleTopics = topics.filter { (subjectId != NEW && it.subjectId == subjectId) || it.subjectId == null || it.id == topicId }
    val catchAll = (subjectId == NEW && CatchAll.isCatchAll(newSubject) && newSubject.isNotBlank()) ||
        (topicId == NEW && CatchAll.isCatchAll(newTopic) && newTopic.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Subject")
                subjects.forEach { s -> Choice(s.title, subjectId == s.id) { subjectId = s.id } }
                Choice("New subject", subjectId == NEW) { subjectId = NEW }
                if (subjectId == NEW) {
                    OutlinedTextField(newSubject, { newSubject = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. Jigs and Fixtures") })
                }
                Label("Topic")
                visibleTopics.forEach { t ->
                    Choice(t.title + if (t.subjectId == null) "  (no subject yet)" else "", topicId == t.id) { topicId = t.id }
                }
                Choice("New topic", topicId == NEW) { topicId = NEW }
                if (topicId == NEW) {
                    OutlinedTextField(newTopic, { newTopic = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. RFP to project kickoff") })
                }
                if (catchAll) Text("Use a specific name, not a catch-all like General.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (showTypeAndPrivacy) {
                    Label("Type")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EntryType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                    }
                    Label("Privacy")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Privacy.entries.forEach { p -> FilterChip(selected = privacy == p, onClick = { privacy = p }, label = { Text(p.label) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = subjectPick != null && topicPick != null && !catchAll,
                onClick = { onConfirm(FilingChoice(subjectPick!!, topicPick!!, type, privacy)) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
