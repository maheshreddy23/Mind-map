package com.freshernotes.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.data.db.TurnEntity
import com.freshernotes.app.ui.BackTopBar
import com.freshernotes.core.Speaker

@Composable
fun SessionScreen(onBack: () -> Unit, vm: SessionViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val turns by vm.turns.collectAsStateWithLifecycle()
    val ended = session?.endedAt != null
    Scaffold(
        topBar = {
            BackTopBar(session?.title ?: "Session", onBack) {
                if (!ended) TextButton(onClick = vm::wrapUp) { Text("Wrap up") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Transcript(turns, Modifier.weight(1f))
            if (!ended) TypedInput(onSend = vm::submitText)
        }
    }
}

@Composable
private fun Transcript(turns: List<TurnEntity>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }
    LazyColumn(
        modifier.fillMaxWidth().testTag("transcript"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        items(turns, key = { it.id }) { TurnBubble(it) }
    }
}

@Composable
private fun TurnBubble(turn: TurnEntity) {
    val fresher = turn.speaker == Speaker.FRESHER.name
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fresher) Arrangement.Start else Arrangement.End) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (fresher) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (fresher) "Arjun (fresher)" else "You", style = MaterialTheme.typography.labelMedium)
                Text(turn.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TypedInput(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).testTag("typedInput"),
            placeholder = { Text("Type instead of speaking…") },
        )
        IconButton(onClick = { onSend(text); text = "" }) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
