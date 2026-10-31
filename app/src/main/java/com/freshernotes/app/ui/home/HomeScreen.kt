package com.freshernotes.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.ui.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSession: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val topics by vm.topics.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fresher Notes") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.startSession(onOpenSession) },
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                text = { Text("Start session") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader("Topics") }
            if (topics.isEmpty()) item { EmptyRow("No topics yet. Notes appear here after your first session.") }
            items(topics, key = { "t${it.id}" }) { t ->
                ListItem(
                    headlineContent = { Text(t.title) },
                    supportingContent = { Text("Updated " + formatTime(t.updatedAt)) },
                    modifier = Modifier.clickable { onOpenTopic(t.id) },
                )
            }
            item { HorizontalDivider() }
            item { SectionHeader("Recent sessions") }
            if (recent.isEmpty()) item { EmptyRow("No sessions yet.") }
            items(recent, key = { "s${it.id}" }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(if (s.endedAt == null) "In progress" else "Ended " + formatTime(s.endedAt)) },
                    modifier = Modifier.clickable { onOpenSession(s.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))

@Composable
private fun EmptyRow(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
