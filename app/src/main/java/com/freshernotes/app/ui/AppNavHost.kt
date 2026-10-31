package com.freshernotes.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freshernotes.app.ui.home.HomeScreen
import com.freshernotes.app.ui.models.ModelSection
import com.freshernotes.app.ui.notes.NotesScreen
import com.freshernotes.app.ui.session.SessionScreen
import com.freshernotes.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SESSION = "session/{sessionId}"
    const val NOTES = "notes/{topicId}"
    const val SETTINGS = "settings"
    fun session(id: Long) = "session/$id"
    fun notes(id: Long) = "notes/$id"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSession = { nav.navigate(Routes.session(it)) },
                onOpenTopic = { nav.navigate(Routes.notes(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SESSION, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {
            SessionScreen(onBack = { nav.popBackStack() }, onOpenTopic = { nav.navigate(Routes.notes(it)) })
        }
        composable(Routes.NOTES, arguments = listOf(navArgument("topicId") { type = NavType.LongType })) {
            NotesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, modelSection = { ModelSection() })
        }
    }
}
