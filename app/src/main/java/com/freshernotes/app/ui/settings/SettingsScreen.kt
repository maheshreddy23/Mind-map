package com.freshernotes.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.ui.BackTopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    modelSection: @Composable () -> Unit = {},
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            modelSection()

            Header("Voice")
            Row {
                FilterChip(selected = s.voice == "piper", onClick = { vm.update { it.copy(voice = "piper") } }, label = { Text("Piper (offline)") })
                Text("  ")
                FilterChip(selected = s.voice == "system", onClick = { vm.update { it.copy(voice = "system") } }, label = { Text("Android TTS") })
            }
            Text("Speech rate: %.1fx".format(s.speechRate))
            Slider(value = s.speechRate, onValueChange = { v -> vm.update { it.copy(speechRate = v) } }, valueRange = 0.5f..2f)
            HorizontalDivider()

            Header("Fresher")
            Text("Max reply length: ${s.maxReplyWords} words")
            Slider(
                value = s.maxReplyWords.toFloat(),
                onValueChange = { v -> vm.update { it.copy(maxReplyWords = v.roundToInt()) } },
                valueRange = 10f..80f,
            )
            Text(if (s.questionEveryNTurns == 1) "Asks a question after every turn" else "Asks a question every ${s.questionEveryNTurns} turns")
            Slider(
                value = s.questionEveryNTurns.toFloat(),
                onValueChange = { v -> vm.update { it.copy(questionEveryNTurns = v.roundToInt()) } },
                valueRange = 1f..5f,
                steps = 3,
            )
            HorizontalDivider()

            Header("Listening")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Continuous mode (voice activity detection)", Modifier.weight(1f))
                Switch(checked = s.continuousMode, onCheckedChange = { c -> vm.update { it.copy(continuousMode = c) } })
            }
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
