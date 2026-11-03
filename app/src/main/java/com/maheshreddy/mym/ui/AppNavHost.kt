package com.maheshreddy.mym.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maheshreddy.mym.ui.components.MindBottomBar
import com.maheshreddy.mym.ui.components.Tab
import com.maheshreddy.mym.ui.home.HomeScreen
import com.maheshreddy.mym.ui.library.EntryScreen
import com.maheshreddy.mym.ui.library.InboxScreen
import com.maheshreddy.mym.ui.library.LibraryScreen
import com.maheshreddy.mym.ui.library.SubjectScreen
import com.maheshreddy.mym.ui.library.TopicScreen
import com.maheshreddy.mym.ui.session.SessionScreen
import com.maheshreddy.mym.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val INBOX = "inbox"
    const val ASK = "ask?q={q}"
    const val BUTLER = "butler"
    const val RESULTS = "results/{sessionId}"
    const val SESSION = "session/{sessionId}"
    const val SUBJECT = "subject/{subjectId}"
    const val TOPIC = "topic/{topicId}"
    const val ENTRY = "entry/{entryId}"
    const val SETTINGS = "settings"
    const val MODELS = "models?category={category}"
    fun models(category: String? = null) = "models?category=" + (category ?: "")
    fun session(id: Long) = "session/$id"
    fun results(sessionId: Long) = "results/$sessionId"
    fun subject(id: Long) = "subject/$id"
    fun topic(id: Long) = "topic/$id"
    fun entry(id: Long) = "entry/$id"
    fun ask(question: String = "") = "ask?q=" + android.net.Uri.encode(question)

    val tabs = mapOf(HOME to Tab.HOME, LIBRARY to Tab.LIBRARY, ASK to Tab.ASK, INBOX to Tab.INBOX)
    fun tabPath(t: Tab) = when (t) {
        Tab.HOME -> HOME
        Tab.LIBRARY -> LIBRARY
        Tab.ASK -> ask()
        Tab.INBOX -> INBOX
    }
}

private const val MOTION = 220

// Material shared-axis X for pushed screens; tabs cross-fade; Session fades to dark (design-spec §5).
private val push: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (targetState.destination.route == Routes.SESSION) fadeIn(tween(MOTION))
    else if (targetState.destination.route in Routes.tabs) fadeIn(tween(MOTION))
    else slideInHorizontally(tween(MOTION)) { it / 8 } + fadeIn(tween(MOTION))
}
private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (targetState.destination.route in Routes.tabs && initialState.destination.route in Routes.tabs) fadeOut(tween(MOTION))
    else slideOutHorizontally(tween(MOTION)) { -it / 8 } + fadeOut(tween(MOTION))
}
private val pop: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(MOTION)) { -it / 8 } + fadeIn(tween(MOTION))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (initialState.destination.route == Routes.SESSION) fadeOut(tween(MOTION))
    else slideOutHorizontally(tween(MOTION)) { it / 8 } + fadeOut(tween(MOTION))
}

@Composable
fun AppNavHost(pendingTalk: kotlinx.coroutines.flow.MutableStateFlow<String?>? = null, talk: TalkViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val openSession: (Long) -> Unit = { nav.navigate(Routes.session(it)) }
    val openSubject: (Long) -> Unit = { nav.navigate(Routes.subject(it)) }
    val openTopic: (Long) -> Unit = { nav.navigate(Routes.topic(it)) }
    val openEntry: (Long) -> Unit = { nav.navigate(Routes.entry(it)) }
    val openInbox: () -> Unit = { nav.selectTab(Routes.INBOX) }
    val onTalk: () -> Unit = { talk.start(openSession) }
    val current by nav.currentBackStackEntryAsState()
    val tab = Routes.tabs[current?.destination?.route]
    val inboxCount by talk.inboxCount.collectAsStateWithLifecycle()
    val butlerName by talk.butlerName.collectAsStateWithLifecycle()
    // "Talk now" from a nudge notification.
    val requestedTalk = pendingTalk?.collectAsStateWithLifecycle()?.value
    androidx.compose.runtime.LaunchedEffect(requestedTalk) {
        if (requestedTalk != null) {
            talk.start(openSession, topic = requestedTalk.ifEmpty { null })
            pendingTalk.value = null
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(com.maheshreddy.mym.ui.components.LocalButlerName provides butlerName) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav, startDestination = Routes.HOME,
                enterTransition = push, exitTransition = pushExit, popEnterTransition = pop, popExitTransition = popExit,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSession = openSession, onOpenTopic = openTopic, onOpenSubject = openSubject,
                        onOpenInbox = openInbox, onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenButler = { nav.navigate(Routes.BUTLER) },
                        onTalkAbout = { topic -> talk.start(openSession, topic = topic) },
                    )
                }
                composable(Routes.ASK, arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { entry ->
                    com.maheshreddy.mym.ui.ask.AskScreen(
                        initialQuestion = entry.arguments?.getString("q"),
                        onOpenEntry = openEntry,
                        onInterview = { q -> talk.start(openSession, topic = q) },
                    )
                }
                composable(Routes.BUTLER) { com.maheshreddy.mym.ui.butler.ButlerScreen(onBack = back, onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
                composable(Routes.LIBRARY) {
                    LibraryScreen(onOpenSubject = openSubject, onOpenTopic = openTopic, onOpenEntry = openEntry, onOpenSession = openSession, onTalk = onTalk)
                }
                composable(Routes.INBOX) { InboxScreen(onBack = null, onOpenEntry = openEntry, onOpenTopic = openTopic, onOpenSession = openSession) }
                composable(Routes.RESULTS, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {
                    InboxScreen(onBack = back, onOpenEntry = openEntry, onOpenTopic = openTopic)
                }
                composable(Routes.SESSION, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {
                    SessionScreen(
                        onBack = back,
                        onOpenResults = { id ->
                            nav.navigate(Routes.results(id)) { popUpTo(Routes.SESSION) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.SUBJECT, arguments = listOf(navArgument("subjectId") { type = NavType.LongType })) {
                    SubjectScreen(onBack = back, onOpenTopic = openTopic, onTalk = onTalk)
                }
                composable(Routes.TOPIC, arguments = listOf(navArgument("topicId") { type = NavType.LongType })) {
                    TopicScreen(
                        onBack = back, onOpenEntry = openEntry,
                        onContinue = { title -> talk.start(openSession, topic = title) },
                        onAsk = { title -> nav.navigate(Routes.ask("What have I said about $title?")) },
                    )
                }
                composable(Routes.ENTRY, arguments = listOf(navArgument("entryId") { type = NavType.LongType })) {
                    EntryScreen(onBack = back, onOpenTopic = openTopic, onOpenSession = openSession)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = back,
                        onOpenModels = { c -> nav.navigate(Routes.models(c)) },
                        onOpenButler = { nav.navigate(Routes.BUTLER) },
                    )
                }
                composable(Routes.MODELS, arguments = listOf(navArgument("category") { type = NavType.StringType; defaultValue = "" })) {
                    com.maheshreddy.mym.ui.models.ModelManagerScreen(onBack = back)
                }
            }
        }
        if (tab != null) {
            MindBottomBar(
                selected = tab, inboxCount = inboxCount,
                onTab = { t -> nav.selectTab(Routes.tabPath(t)) },
                onTalk = onTalk,
            )
        }
    }
    }
}

/** Bottom-bar navigation: one copy of each tab, state kept when switching. */
private fun NavHostController.selectTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
