package com.freshernotes.app.ui.butler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.ButlerRepository
import com.freshernotes.app.ui.components.Avatar
import com.freshernotes.app.ui.components.BackBar
import com.freshernotes.app.ui.components.Caption
import com.freshernotes.app.ui.components.MindCard
import com.freshernotes.app.ui.components.Pill
import com.freshernotes.app.ui.components.PillStyle
import com.freshernotes.app.ui.components.SectionLabel
import com.freshernotes.app.ui.theme.Mind
import com.freshernotes.app.ui.theme.MindDimens
import com.freshernotes.core.llm.ButlerProfile
import com.freshernotes.core.llm.ButlerPrompt
import com.freshernotes.core.llm.InterviewStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ButlerUiState(val loaded: Boolean = false, val profile: ButlerProfile = ButlerProfile(), val journal: List<String> = emptyList())

@HiltViewModel
class ButlerViewModel @Inject constructor(private val repo: ButlerRepository) : ViewModel() {
    val state = combine(repo.profile, repo.journal) { p, j -> ButlerUiState(true, p, j) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ButlerUiState())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun save(p: ButlerProfile) = viewModelScope.launch {
        repo.save(p)
        _message.value = "Saved. ${p.displayName} uses this from the next question."
    }

    fun clearJournal() = viewModelScope.launch { repo.clearJournal(); _message.value = "Journal cleared" }
    fun clearMessage() { _message.value = null }
}

@Composable
fun ButlerScreen(onBack: () -> Unit, vm: ButlerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    if (state.loaded) ButlerContent(state, snackbar, onBack, onSave = { vm.save(it) }, onClearJournal = { vm.clearJournal() })
}

/** design-spec §4 Butler: name, how it addresses you, personality sliders, interview style, journal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButlerContent(
    state: ButlerUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSave: (ButlerProfile) -> Unit,
    onClearJournal: () -> Unit,
) {
    val c = Mind.colors
    val p0 = state.profile
    var name by rememberSaveable { mutableStateOf(p0.name) }
    var callsYou by rememberSaveable { mutableStateOf(p0.callsYou) }
    var casual by rememberSaveable { mutableStateOf(p0.casual) }
    var warm by rememberSaveable { mutableStateOf(p0.warm) }
    var witty by rememberSaveable { mutableStateOf(p0.witty) }
    var detailed by rememberSaveable { mutableStateOf(p0.detailed) }
    var style by rememberSaveable { mutableStateOf(p0.style) }
    var quirks by rememberSaveable { mutableStateOf(p0.quirks) }
    val draft = ButlerProfile(name, callsYou, casual, warm, witty, detailed, style, quirks)

    Scaffold(
        containerColor = c.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { BackBar(onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(c.ground).navigationBarsPadding().padding(horizontal = MindDimens.screenPadding, vertical = 12.dp)) {
                Pill("Save personality", { onSave(draft) }, Modifier.fillMaxWidth().testTag("saveButler"), style = PillStyle.INK, height = 48.dp)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = MindDimens.screenPadding).testTag("butler"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Avatar(draft.initial, onClick = null, size = 64.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Your butler", style = Mind.type.screenTitle.copy(fontSize = Mind.type.screenTitle.fontSize.times(26f / 30f)), color = c.ink)
                    Caption(ButlerPrompt.summary(draft))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", name, { name = it }, Modifier.weight(1f))
                Field("Calls you", callsYou, { callsYou = it }, Modifier.weight(1f), placeholder = "Your name")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Personality")
                Lever("Formal", "Casual", casual) { casual = it }
                Lever("Reserved", "Warm", warm) { warm = it }
                Lever("Serious", "Witty", witty) { witty = it }
                Lever("Brief", "Detailed", detailed) { detailed = it }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Interview style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterviewStyle.entries.forEach { s ->
                        val on = s == style
                        Box(
                            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                                .background(if (on) c.accent else c.surface)
                                .then(if (on) Modifier else Modifier.border(1.dp, c.fieldLine, RoundedCornerShape(20.dp)))
                                .clickable(role = Role.RadioButton) { style = s }.padding(horizontal = 14.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Text(s.label, style = if (on) Mind.type.button.copy(fontSize = Mind.type.label.fontSize) else Mind.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) c.onAccent else c.ink)
                        }
                    }
                }
                Caption(style.hint)
            }
            Field("Character notes (optional)", quirks, { quirks = it }, Modifier.fillMaxWidth(), placeholder = "e.g. quotes old engineering proverbs, never says \"awesome\"", singleLine = false)
            MindCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionLabel("${draft.displayName}'s journal", Modifier.weight(1f))
                    if (state.journal.isNotEmpty()) Pill("Clear", onClearJournal, style = PillStyle.TEXT, height = 36.dp)
                }
                if (state.journal.isEmpty()) {
                    Text("Nothing learned yet. When you correct where I file something, I note it here and file that way next time.", style = Mind.type.body, color = c.ink)
                } else {
                    state.journal.take(5).forEach { Text(it, style = Mind.type.body, color = c.ink) }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, placeholder: String = "", singleLine: Boolean = true) {
    val c = Mind.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption(label)
        Box(
            Modifier.fillMaxWidth().heightIn(min = MindDimens.touch).clip(RoundedCornerShape(12.dp)).background(c.surface)
                .border(1.dp, c.fieldLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = Mind.type.body, color = c.muted)
            BasicTextField(
                value, onChange, singleLine = singleLine, textStyle = Mind.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun Lever(left: String, right: String, value: Float, onChange: (Float) -> Unit) {
    val c = Mind.colors
    Column {
        Row {
            Text(left, style = Mind.type.caption, color = c.neutralChipText, modifier = Modifier.weight(1f))
            Text(right, style = Mind.type.caption, color = c.neutralChipText)
        }
        Slider(
            value = value, onValueChange = onChange,
            modifier = Modifier.semantics { contentDescription = "$left to $right" },
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
        )
    }
}
