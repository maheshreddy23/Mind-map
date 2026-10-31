package com.freshernotes.app.ui.notes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.ui.BackTopBar

@Composable
fun NotesScreen(onBack: () -> Unit, vm: NotesViewModel = hiltViewModel()) {
    val topic by vm.topic.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            BackTopBar(topic?.title ?: "Notes", onBack) {
                if (editing) {
                    IconButton(onClick = { vm.save(draft); editing = false }) { Icon(Icons.Default.Check, "Save") }
                } else {
                    IconButton(onClick = { draft = topic?.markdown.orEmpty(); editing = true }) { Icon(Icons.Default.Edit, "Edit") }
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        if (editing) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = mod)
        } else {
            MarkdownView(topic?.markdown.orEmpty(), mod.verticalScroll(rememberScrollState()))
        }
    }
}
