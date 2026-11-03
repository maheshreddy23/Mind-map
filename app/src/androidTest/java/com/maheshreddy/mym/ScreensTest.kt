package com.maheshreddy.mym

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maheshreddy.mym.audio.ListenState
import com.maheshreddy.mym.core.llm.InterviewStyle
import com.maheshreddy.mym.core.map.KnowledgeMap
import com.maheshreddy.mym.core.map.MapHit
import com.maheshreddy.mym.data.settings.AppSettings
import com.maheshreddy.mym.engine.EngineStatus
import com.maheshreddy.mym.session.FresherState
import com.maheshreddy.mym.ui.map.MapActions
import com.maheshreddy.mym.ui.map.MapContent
import com.maheshreddy.mym.ui.map.MapUiState
import com.maheshreddy.mym.ui.onboarding.OnboardingActions
import com.maheshreddy.mym.ui.onboarding.OnboardingContent
import com.maheshreddy.mym.ui.onboarding.OnboardingStep
import com.maheshreddy.mym.ui.onboarding.OnboardingUiState
import com.maheshreddy.mym.ui.session.SessionContent
import com.maheshreddy.mym.ui.session.SessionUiState
import com.maheshreddy.mym.ui.settings.SettingsActions
import com.maheshreddy.mym.ui.settings.SettingsContent
import com.maheshreddy.mym.ui.settings.SettingsUiState
import com.maheshreddy.mym.ui.theme.MindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings, onboarding, the session's Send/Wrap up button and the Map, driven through their stateless content. */
@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsHasEverySectionAndTogglesWork() {
        var settings by mutableStateOf(AppSettings())
        var opened: String? = "none"
        compose.setContent {
            MindTheme {
                SettingsContent(
                    SettingsUiState(settings = settings), remember { SnackbarHostState() }, {},
                    SettingsActions(update = { settings = it(settings) }, openModels = { opened = it }),
                )
            }
        }
        for (s in listOf("Models", "Speech recognition", "Voice", "Butler", "Nudges", "Privacy", "Storage", "Backup", "Performance", "About")) {
            compose.onNodeWithTag("settings").performScrollToNode(hasText(s))
            compose.onNodeWithText(s).assertExists()
        }
        compose.onNodeWithTag("settings").performScrollToNode(hasText("Open the model manager"))
        compose.onNodeWithText("Open the model manager").performClick()
        assertEquals(null, opened)
        compose.onNodeWithTag("settings").performScrollToNode(hasContentDescription("Daily capture prompt"))
        compose.onNodeWithContentDescription("Daily capture prompt").performClick()
        assertFalse(settings.nudgeDaily)
    }

    @Test
    fun onboardingWalksThroughEveryStep() {
        var state by mutableStateOf(OnboardingUiState(device = "OnePlus CPH2691 · SM8650 · 12 GB RAM", tier = "Flagship", ramGb = 12))
        var started = false
        val next = { state = state.copy(step = OnboardingStep.entries[state.step.ordinal + 1]) }
        compose.setContent {
            MindTheme {
                OnboardingContent(
                    state,
                    OnboardingActions(
                        onNext = next, onName = { state = state.copy(name = it) }, onStyle = { state = state.copy(style = it) },
                        onSaveButler = next, onAllowMic = next, onStartSession = { started = true },
                    ),
                )
            }
        }
        compose.onNodeWithText("MYM – Map your Mind").assertExists()
        compose.onNodeWithTag("onboardingNext").performClick()
        compose.onNodeWithTag("deviceDescription", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("onboardingNext").performClick()
        compose.onNodeWithTag("onboarding_BUNDLE").assertExists()
        compose.onNodeWithTag("bundleTotal").assertExists()
        compose.runOnIdle { state = state.copy(step = OnboardingStep.BUTLER, name = "") }
        compose.onNodeWithTag("butlerNameField").performTextInput("Alfie")
        compose.onNodeWithTag("style_CURIOUS_FRESHER").performClick()
        assertEquals("Alfie", state.name)
        assertEquals(InterviewStyle.CURIOUS_FRESHER, state.style)
        compose.onNodeWithTag("onboardingNext").performClick()
        compose.onNodeWithTag("allowMic").performClick()
        compose.onNodeWithTag("startFirstSession").performClick()
        assertTrue(started)
    }

    private val live = SessionUiState(
        title = "Session", ended = false, turns = emptyList(), listen = ListenState.Idle, level = 0f, butler = FresherState.Idle,
        engines = EngineStatus(), deviceWarning = null, notesStatus = "NONE", filed = 0, waiting = 0,
    )

    @Test
    fun sendBecomesWrapUpAndWrapUpCanBeUndone() {
        var state by mutableStateOf(live)
        var sent = ""
        compose.setContent {
            MindTheme {
                SessionContent(
                    state, {}, onWrapUp = { state = state.copy(wrapPending = true) }, {}, { sent = it }, {}, {}, {}, {},
                    onUndoWrap = { state = state.copy(wrapPending = false) },
                )
            }
        }
        compose.onNodeWithContentDescription("Wrap up").assertExists()
        compose.onNodeWithTag("typedInput").performTextInput("Bushes wear")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals("Bushes wear", sent)
        compose.onNodeWithContentDescription("Wrap up").performClick()
        compose.onNodeWithText("Wrapping up…").assertExists()
        compose.onNodeWithTag("undoWrap").performClick()
        compose.onNodeWithTag("wrapSnackbar").assertDoesNotExist()
        compose.onNodeWithTag("sessionMenu").performClick()
        compose.onNodeWithText("Rename").assertExists()
        compose.onNodeWithText("Delete session").assertExists()
    }

    @Test
    fun mapPreviewOffersInterviewForGaps() {
        val layout = KnowledgeMap.build(emptyList(), emptyList(), emptyList(), starters = listOf("Jigs and Fixtures", "PLM"))
        var talked = ""
        var state by mutableStateOf(MapUiState(layout, loading = false))
        compose.setContent { MindTheme { MapContent(state, MapActions(onSelect = { state = state.copy(selected = it) }, onTalkAbout = { talked = it })) } }
        compose.onNodeWithTag("mapCanvas").assertExists()
        compose.runOnIdle { state = state.copy(selected = MapHit.Subject(layout.clusters.first())) }
        compose.onNodeWithTag("mapTalk").performClick()
        assertEquals("Jigs and Fixtures", talked)
    }
}
