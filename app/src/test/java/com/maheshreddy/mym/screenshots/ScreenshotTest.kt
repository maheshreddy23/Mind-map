package com.maheshreddy.mym.screenshots

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.maheshreddy.mym.audio.ListenState
import com.maheshreddy.mym.data.db.EntryEntity
import com.maheshreddy.mym.engine.EngineStatus
import com.maheshreddy.mym.engine.LoadState
import com.maheshreddy.mym.search.DocKind
import com.maheshreddy.mym.search.SearchHit
import com.maheshreddy.mym.session.FresherState
import com.maheshreddy.mym.ui.components.MindBottomBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import com.maheshreddy.mym.ui.components.Tab
import com.maheshreddy.mym.ui.home.HomeContent
import com.maheshreddy.mym.ui.home.HomeUiState
import com.maheshreddy.mym.ui.home.SessionRow
import com.maheshreddy.mym.ui.home.ShelfTile
import com.maheshreddy.mym.ui.library.FiledItem
import com.maheshreddy.mym.ui.library.InboxContent
import com.maheshreddy.mym.ui.library.InboxItem
import com.maheshreddy.mym.ui.library.InboxUiState
import com.maheshreddy.mym.ui.library.LibraryContent
import com.maheshreddy.mym.ui.library.LibraryUiState
import com.maheshreddy.mym.ui.library.Shelf
import com.maheshreddy.mym.ui.library.TopicContent
import com.maheshreddy.mym.ui.library.TopicTile
import com.maheshreddy.mym.ui.library.TopicUiState
import com.maheshreddy.mym.ui.session.SessionContent
import com.maheshreddy.mym.ui.session.SessionTurn
import com.maheshreddy.mym.ui.session.SessionUiState
import com.maheshreddy.mym.ui.session.TurnTiming
import com.maheshreddy.mym.ui.theme.MindTheme
import com.maheshreddy.mym.core.Speaker
import com.maheshreddy.mym.core.library.Privacy
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * design-spec §6: a screenshot per screen in light and dark. Goldens: app/src/test/screenshots.
 * Record with ./gradlew recordRoborazziDebug; CI runs verifyRoborazziDebug and fails on a visual change.
 * Uses a plain Application so the Hilt app (database, engines) never starts.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = Application::class)
class ScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))

        private val options by lazy { RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)) }
    }

    private val theme get() = if (dark) "dark" else "light"

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/${name}_$theme.png", roborazziOptions = options) { MindTheme(dark = dark) { content() } }

    @Test
    fun home() = shot("home") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                HomeContent(Samples.home, {}, {}, {}, {}, {})
            }
            MindBottomBar(Tab.HOME, onTab = {}, onTalk = {})
        }
    }

    @Test
    fun homeEmpty() = shot("home_empty") {
        HomeContent(HomeUiState(greeting = "Good morning", missingModels = listOf("Speech-to-text", "Language model")), {}, {}, {}, {}, {})
    }

    @Test
    fun sessionListen() = shot("session_listen") { SessionContent(Samples.liveSession.copy(listenOnly = true, listen = ListenState.Idle, level = 0f), {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun ask() = shot("ask") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                com.maheshreddy.mym.ui.ask.AskContent(Samples.ask, {}, {}, {}, {})
            }
            MindBottomBar(Tab.ASK, onTab = {}, onTalk = {})
        }
    }

    @Test
    fun butler() = shot("butler") {
        com.maheshreddy.mym.ui.butler.ButlerContent(
            com.maheshreddy.mym.ui.butler.ButlerUiState(
                true, com.maheshreddy.mym.core.llm.ButlerProfile(callsYou = "Mahesh"),
                listOf("\"Protect timeline and budget together\" belongs under Project Management › Trade-offs, not Project Management › Risk."),
            ),
            remember { SnackbarHostState() }, {}, {}, {},
        )
    }

    @Test
    fun session() = shot("session") { SessionContent(Samples.liveSession, {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun sessionEnded() = shot("session_ended") {
        SessionContent(Samples.liveSession.copy(ended = true, listen = ListenState.Idle, level = 0f, notesStatus = "DONE", filed = 1, waiting = 1), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun sessionWrapPending() = shot("session_wrap_pending") {
        SessionContent(Samples.liveSession.copy(listen = ListenState.Idle, level = 0f, wrapPending = true), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun filingSheet() = shot("filing_sheet") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.maheshreddy.mym.ui.theme.Mind.colors.ground)) {
            com.maheshreddy.mym.ui.library.FilingResults(Samples.wrapUp, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun map() = shot("map") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                com.maheshreddy.mym.ui.map.MapContent(Samples.map, com.maheshreddy.mym.ui.map.MapActions())
            }
            MindBottomBar(Tab.MAP, onTab = {}, onTalk = {})
        }
    }

    @Test
    fun mapPreview() = shot("map_preview") {
        val m = Samples.map
        com.maheshreddy.mym.ui.map.MapContent(m.copy(selected = com.maheshreddy.mym.core.map.MapHit.Topic(m.layout.nodes.first { it.id == 1L })), com.maheshreddy.mym.ui.map.MapActions())
    }

    @Test
    fun mapEmpty() = shot("map_empty") {
        com.maheshreddy.mym.ui.map.MapContent(
            com.maheshreddy.mym.ui.map.MapUiState(
                com.maheshreddy.mym.core.map.KnowledgeMap.build(emptyList(), emptyList(), emptyList(), starters = com.maheshreddy.mym.data.settings.AppSettings.DEFAULT_STARTERS),
                loading = false,
            ),
            com.maheshreddy.mym.ui.map.MapActions(),
        )
    }

    @Test
    fun wrapUp() = shot("wrapup") {
        InboxContent(Samples.wrapUp, remember { SnackbarHostState() }, onBack = {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun inbox() = shot("inbox") {
        InboxContent(Samples.wrapUp.copy(sessionMode = false), remember { SnackbarHostState() }, onBack = {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun inboxEmpty() = shot("inbox_empty") {
        InboxContent(
            InboxUiState(
                recentFiled = Samples.wrapUp.filed,
                stats = com.maheshreddy.mym.core.library.LibraryStats(entriesThisWeek = 6, topics = 4, streakDays = 3),
            ),
            remember { SnackbarHostState() }, onBack = {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun askEmpty() = shot("ask_empty") {
        com.maheshreddy.mym.ui.ask.AskContent(
            com.maheshreddy.mym.ui.ask.AskUiState(suggestions = listOf("What is still open on RFP to project kickoff?", "What have I said about RFP to project kickoff?", "What have I said about Trade-offs?", "What lessons have I recorded about Project Management?")),
            {}, {}, {}, {},
        )
    }

    @Test
    fun onboardingBundle() = shot("onboarding_bundle") {
        val rec = com.maheshreddy.mym.core.models.DeviceFit.recommended(com.maheshreddy.mym.core.models.DeviceProfile.of(11_500, "SM8650", 8))
        com.maheshreddy.mym.ui.onboarding.OnboardingContent(
            com.maheshreddy.mym.ui.onboarding.OnboardingUiState(
                step = com.maheshreddy.mym.ui.onboarding.OnboardingStep.BUNDLE,
                bundle = rec.toSortedMap(compareBy { it.ordinal }).values.map { com.maheshreddy.mym.ui.onboarding.BundleItem(it, installed = false, download = null) },
            ),
            com.maheshreddy.mym.ui.onboarding.OnboardingActions(),
        )
    }

    @Test
    fun onboardingButler() = shot("onboarding_butler") {
        com.maheshreddy.mym.ui.onboarding.OnboardingContent(
            com.maheshreddy.mym.ui.onboarding.OnboardingUiState(
                step = com.maheshreddy.mym.ui.onboarding.OnboardingStep.BUTLER, callsYou = "Mahesh",
                areas = listOf("Jigs and Fixtures", "PLM"), areaChoices = com.maheshreddy.mym.data.settings.AppSettings.DEFAULT_STARTERS,
            ),
            com.maheshreddy.mym.ui.onboarding.OnboardingActions(),
        )
    }

    @Test
    fun library() = shot("library") {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                LibraryContent(
                    Samples.library.copy(gaps = listOf(
                        com.maheshreddy.mym.core.library.GapSuggestion("No entries on ECO yet", "ECO"),
                        com.maheshreddy.mym.core.library.GapSuggestion("RFP to project kickoff: an open question to settle", "RFP to project kickoff"),
                    )),
                    {}, {}, {}, {}, {}, {}, {},
                )
            }
            MindBottomBar(Tab.LIBRARY, onTab = {}, onTalk = {})
        }
    }

    @Test
    fun librarySearch() = shot("library_search") { LibraryContent(Samples.library.copy(query = "kickoff", results = Samples.hits), {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun topicSummary() = shot("topic_summary") { TopicContent(Samples.topic, remember { SnackbarHostState() }, {}, {}, {}, {}) }

    @Test
    fun topicEntries() = shot("topic_entries") { TopicContent(Samples.topic, remember { SnackbarHostState() }, {}, {}, {}, {}, initialTab = 1) }
}

/** Fixed, realistic sample data (no clock, no randomness) so screenshots are stable. */
private object Samples {
    private const val T = 1_793_550_000_000L // 1 Nov 2026

    private fun entry(id: Long, title: String, body: String, type: String, status: String, privacy: String = "PRIVATE", sessionId: Long? = 7, topicId: Long? = null, auto: Boolean = false) =
        EntryEntity(
            id = id, sessionId = sessionId, topicId = topicId, title = title, body = body, type = type, privacy = privacy, status = status,
            autoFiled = auto, createdAt = T + id * 60_000, updatedAt = T, filedAt = if (status == "FILED") T else null,
            reason = if (status == "INBOX") "New topic \"Trade-offs\"; low confidence" else "",
        )

    private val kickoff = TopicTile(1, "RFP to project kickoff", "Project Management", entries = 2, openQuestions = 1, privacies = setOf(Privacy.PRIVATE))

    val home = HomeUiState(
        greeting = "Good evening, Mahesh",
        nudge = com.maheshreddy.mym.core.library.Nudge(
            com.maheshreddy.mym.core.library.Nudge.Kind.UNFINISHED_TOPIC,
            "RFP to project kickoff still has an open question. Ten minutes on it?", "RFP to project kickoff",
        ),
        inboxCount = 1,
        continueTopic = kickoff,
        shelves = listOf(ShelfTile(1, "Project Management", 2), ShelfTile(2, "PLM", 0), ShelfTile(3, "Jigs and Fixtures", 0)),
        recent = listOf(
            SessionRow(7, "RFP to project kickoff", "Ended", "From won bid to kickoff; Protect timeline and budget together"),
            SessionRow(6, "Drill jigs", "Ended", "Worn bushes make holes drift"),
        ),
    )

    val liveSession = SessionUiState(
        title = "Session 1 Nov, 22:40",
        ended = false,
        turns = listOf(
            SessionTurn(1, Speaker.SENIOR, "Once we win the bid, a project number is created in PLM, and the kickoff meeting assigns each team its scope.", TurnTiming(9.0, 4.3)),
            SessionTurn(2, Speaker.FRESHER, "Who decides each team's scope at kickoff, and what happens when two teams both claim a task?", null),
        ),
        listen = ListenState.Listening(continuous = false, hearingSpeech = true),
        level = 0.7f,
        butler = FresherState.Idle,
        engines = EngineStatus(whisper = LoadState.READY, llm = LoadState.READY),
        deviceWarning = null, notesStatus = "NONE", filed = 0, waiting = 0,
    )

    private val principle = entry(
        11, "Protect timeline and budget together",
        "- Customers want it faster but won't pay for it.\n- The plan has to protect the timeline and the budget together.",
        "PRINCIPLE", "INBOX",
    )
    private val knowledge = entry(12, "From won bid to kickoff", "- After a bid is won, PLM gets a project number and the kickoff assigns teams.", "KNOWLEDGE", "FILED", topicId = 1, auto = true)
    private val question = entry(13, "Who settles a task two teams both claim?", "- How does CCPM settle a task two teams both claim?", "OPEN_QUESTION", "FILED", topicId = 1, auto = true)

    val wrapUp = InboxUiState(
        sessionMode = true, filedFromSession = 2, waitingFromSession = 1,
        needs = listOf(InboxItem(principle, "Project Management", "Trade-offs", newSubject = false, newTopic = true, canAccept = true)),
        filed = listOf(
            FiledItem(knowledge, "Project Management › RFP to project kickoff", undoable = true),
            FiledItem(question, "Project Management › RFP to project kickoff", undoable = true),
        ),
    )

    val library = LibraryUiState(
        topicCount = 3, entryCount = 4,
        shelves = listOf(
            Shelf(1, "Project Management", listOf(kickoff, TopicTile(2, "Trade-offs", "Project Management", 1, 0, setOf(Privacy.PRIVATE)))),
            Shelf(2, "PLM", emptyList()),
            Shelf(3, "Jigs and Fixtures", emptyList()),
        ),
        unassigned = listOf(TopicTile(5, "Weekend thoughts", null, 1, 0, setOf(Privacy.PRIVATE))),
    )

    val ask = com.maheshreddy.mym.ui.ask.AskUiState(
        listOf(
            com.maheshreddy.mym.ui.ask.Exchange(
                1, "What happens after we win a bid?",
                com.maheshreddy.mym.ui.ask.Reply.Answer(
                    listOf(
                        com.maheshreddy.mym.core.library.AnswerPart.Text("You create the project number in PLM"),
                        com.maheshreddy.mym.core.library.AnswerPart.Cite(1, 12),
                        com.maheshreddy.mym.core.library.AnswerPart.Text(", then a kickoff meeting assigns the teams, and CCPM allocates their tasks"),
                        com.maheshreddy.mym.core.library.AnswerPart.Cite(2, 13),
                        com.maheshreddy.mym.core.library.AnswerPart.Text(". You haven't yet said how CCPM settles a disputed task."),
                    ),
                    citedCount = 2,
                ),
            ),
            com.maheshreddy.mym.ui.ask.Exchange(2, "What have I said about datum selection?", com.maheshreddy.mym.ui.ask.Reply.Nothing),
        ),
    )

    val map = run {
        fun e(t: Long, text: String, day: Int, type: com.maheshreddy.mym.core.library.EntryType = com.maheshreddy.mym.core.library.EntryType.KNOWLEDGE) =
            com.maheshreddy.mym.core.map.MapEntryIn(t, T + day * 86_400_000L, Privacy.PRIVATE, type, text)
        com.maheshreddy.mym.ui.map.MapUiState(
            com.maheshreddy.mym.core.map.KnowledgeMap.build(
                subjects = listOf(
                    com.maheshreddy.mym.core.map.MapSubject(1, "Project Management"),
                    com.maheshreddy.mym.core.map.MapSubject(2, "Jigs and Fixtures"),
                    com.maheshreddy.mym.core.map.MapSubject(3, "PLM"),
                ),
                topics = listOf(
                    com.maheshreddy.mym.core.map.MapTopicIn(1, 1, "RFP to project kickoff", "From a won bid to the kickoff meeting, and who owns which scope."),
                    com.maheshreddy.mym.core.map.MapTopicIn(2, 1, "Trade-offs"),
                    com.maheshreddy.mym.core.map.MapTopicIn(3, 2, "Drill jigs"),
                    com.maheshreddy.mym.core.map.MapTopicIn(4, 2, "Datum selection"),
                ),
                entries = listOf(
                    e(1, "won bid kickoff project number PLM teams scope", 1), e(1, "kickoff assigns teams scope CCPM", 2),
                    e(1, "CCPM settles disputed task", 3, com.maheshreddy.mym.core.library.EntryType.OPEN_QUESTION),
                    e(2, "protect timeline budget together customers scope", 4),
                    e(3, "drill bushes guide holes datum faces", 5), e(3, "worn bushes holes drift", 6), e(3, "check bush wear batch", 7), e(3, "drill jig plates", 8),
                    e(4, "datum faces locate holes drill", 9),
                ),
                confirmed = setOf(3L to 4L),
            ),
            loading = false,
        )
    }

    val hits = listOf(
        SearchHit(DocKind.TOPIC, 1, "RFP to project kickoff", "…the **kickoff** meeting assigns each team…"),
        SearchHit(DocKind.ENTRY, 12, "From won bid to kickoff", "After a bid is won, PLM gets a project number and the **kickoff** assigns teams."),
        SearchHit(DocKind.SESSION, 7, "Session 1 Nov, 22:40", "…and the **kickoff** meeting assigns each team its scope…"),
    )

    val topic = TopicUiState(
        title = "RFP to project kickoff", subject = "Project Management",
        summary = "A won RFP becomes a project in PLM, and the kickoff meeting divides the scope between teams. Commercial terms shape the design requirements from the start.",
        updatedAt = T,
        entries = listOf(
            knowledge,
            entry(14, "Read the commercial terms early", "- Commercial terms set the design requirements, so ignoring them early creates plans that can't be delivered.", "LESSON", "FILED", topicId = 1),
            question,
        ),
    )
}
