package com.freshernotes.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshernotes.app.ui.components.Avatar
import com.freshernotes.app.ui.components.BUTLER_NAME
import com.freshernotes.app.ui.components.Caption
import com.freshernotes.app.ui.components.MindCard
import com.freshernotes.app.ui.components.Pill
import com.freshernotes.app.ui.components.PillStyle
import com.freshernotes.app.ui.components.ScreenTitle
import com.freshernotes.app.ui.components.SectionLabel
import com.freshernotes.app.ui.library.LibraryUi
import com.freshernotes.app.ui.library.TopicTile
import com.freshernotes.app.ui.theme.Mind
import com.freshernotes.app.ui.theme.MindDimens

data class ShelfTile(val id: Long, val title: String, val topics: Int)
data class SessionRow(val id: Long, val title: String, val status: String)

data class HomeUiState(
    val greeting: String = "Good evening",
    val missingModels: List<String> = emptyList(),
    val nudge: com.freshernotes.core.library.Nudge? = null,
    val inboxCount: Int = 0,
    val continueTopic: TopicTile? = null,
    val shelves: List<ShelfTile> = emptyList(),
    val recent: List<SessionRow> = emptyList(),
)

@Composable
fun HomeScreen(
    onOpenSession: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenSubject: (Long) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenButler: () -> Unit = {},
    onTalkAbout: (String?) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, onOpenSession, onOpenTopic, onOpenSubject, onOpenInbox, onOpenSettings, onOpenButler, onTalkAbout, vm::snoozeNudge)
}

/** design-spec §4 Home (the nudge card arrives with nudges in phase 4). */
@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenSession: (Long) -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenSubject: (Long) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenButler: () -> Unit = {},
    onTalkAbout: (String?) -> Unit = {},
    onLater: () -> Unit = {},
) {
    val c = Mind.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.ground).statusBarsPadding().testTag("home"),
        contentPadding = PaddingValues(start = MindDimens.screenPadding, end = MindDimens.screenPadding, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.greeting, style = Mind.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.muted)
                    ScreenTitle("What shall we shelve today?")
                }
                Avatar(BUTLER_NAME.take(1).uppercase(), onClick = onOpenButler)
            }
        }
        if (state.missingModels.isNotEmpty()) item {
            MindCard(onClick = onOpenSettings, background = c.amberSoft, border = null) {
                Text("Models needed", style = Mind.type.overline, color = c.amberText)
                Text(state.missingModels.joinToString() + ". Pick them in Settings to start talking.", style = Mind.type.body, color = c.ink)
            }
        }
        state.nudge?.let { n ->
            item {
                MindCard(background = c.amberSoft, border = null, modifier = Modifier.testTag("nudge")) {
                    Text("${BUTLER_NAME.uppercase()} · TODAY'S NUDGE", style = Mind.type.overline.copy(fontSize = Mind.type.caption.fontSize), color = c.amberText)
                    Text(n.text, style = Mind.type.bodyLarge.copy(fontSize = Mind.type.bodyLarge.fontSize.times(16f / 17f)), color = c.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Talk now", { onTalkAbout(n.talkAbout) }, style = PillStyle.INK, height = MindDimens.touch)
                        Pill("Later", onLater, style = PillStyle.OUTLINE, height = MindDimens.touch)
                    }
                }
            }
        }
        if (state.inboxCount > 0) item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = MindDimens.touch).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .clickable(role = Role.Button, onClick = onOpenInbox)
                    .then(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)).testTag("inboxStrip"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Text("${state.inboxCount}", style = Mind.type.chip, color = c.onAccent)
                }
                Text(
                    if (state.inboxCount == 1) "Entry waiting for your call" else "${state.inboxCount} entries waiting for your call",
                    style = Mind.type.body, color = c.ink, modifier = Modifier.weight(1f),
                )
                Text("Review", style = Mind.type.button, color = c.accent)
            }
        }
        state.continueTopic?.let { t ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Continue")
                    MindCard(onClick = { onOpenTopic(t.id) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.subject?.let { Caption(it) }
                            Text(t.title, style = Mind.type.cardTitle, color = c.ink)
                            Caption(
                                LibraryUi.plural(t.entries, "entry", "entries") +
                                    if (t.openQuestions > 0) " · " + LibraryUi.plural(t.openQuestions, "open question", "open questions") else "",
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Your shelves")
                if (state.shelves.isEmpty()) {
                    Caption("No shelves yet. Talk, wrap up, and confirm where $BUTLER_NAME files things.")
                }
                state.shelves.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { s ->
                            Column(
                                Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (s.topics > 0) c.accentSoft else c.neutralChip)
                                    .clickable(role = Role.Button) { onOpenSubject(s.id) }.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(s.title, style = Mind.type.label.copy(fontSize = Mind.type.button.fontSize), color = c.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Caption(if (s.topics == 0) "Empty" else LibraryUi.plural(s.topics, "topic", "topics"), color = if (s.topics > 0) c.ink else c.neutralChipText)
                            }
                        }
                        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (state.recent.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Recent sessions")
                state.recent.forEach { s ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = MindDimens.touch).clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { onOpenSession(s.id) }.padding(vertical = 8.dp),
                    ) {
                        Text(s.title, style = Mind.type.body, color = c.ink)
                        Caption(s.status)
                    }
                }
            }
        }
    }
}
