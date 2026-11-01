package com.freshernotes.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freshernotes.app.ui.home.HomeScreen
import com.freshernotes.app.ui.library.EntryScreen
import com.freshernotes.app.ui.library.InboxScreen
import com.freshernotes.app.ui.library.LibraryScreen
import com.freshernotes.app.ui.library.SubjectScreen
import com.freshernotes.app.ui.library.TopicScreen
import com.freshernotes.app.ui.models.ModelSection
import com.freshernotes.app.ui.session.SessionScreen
import com.freshernotes.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SESSION = "session/{sessionId}"
    const val LIBRARY = "library"
    const val INBOX = "inbox"
    const val SUBJECT = "subject/{subjectId}"
    const val TOPIC = "topic/{topicId}"
    const val ENTRY = "entry/{entryId}"
    const val SETTINGS = "settings"
    fun session(id: Long) = "session/$id"
    fun subject(id: Long) = "subject/$id"
    fun topic(id: Long) = "topic/$id"
    fun entry(id: Long) = "entry/$id"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val openSession: (Long) -> Unit = { nav.navigate(Routes.session(it)) }
    val openSubject: (Long) -> Unit = { nav.navigate(Routes.subject(it)) }
    val openTopic: (Long) -> Unit = { nav.navigate(Routes.topic(it)) }
    val openEntry: (Long) -> Unit = { nav.navigate(Routes.entry(it)) }
    val openInbox: () -> Unit = { nav.navigate(Routes.INBOX) }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSession = openSession,
                onOpenTopic = openTopic,
                onOpenEntry = openEntry,
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenInbox = openInbox,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SESSION, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {
            SessionScreen(onBack = back, onOpenEntry = openEntry, onOpenInbox = openInbox)
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(onBack = back, onOpenSubject = openSubject, onOpenTopic = openTopic, onOpenInbox = openInbox)
        }
        composable(Routes.INBOX) { InboxScreen(onBack = back, onOpenEntry = openEntry) }
        composable(Routes.SUBJECT, arguments = listOf(navArgument("subjectId") { type = NavType.LongType })) {
            SubjectScreen(onBack = back, onOpenTopic = openTopic)
        }
        composable(Routes.TOPIC, arguments = listOf(navArgument("topicId") { type = NavType.LongType })) {
            TopicScreen(onBack = back, onOpenEntry = openEntry)
        }
        composable(Routes.ENTRY, arguments = listOf(navArgument("entryId") { type = NavType.LongType })) {
            EntryScreen(onBack = back, onOpenTopic = openTopic, onOpenSession = openSession)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, modelSection = { ModelSection() })
        }
    }
}
