package com.freshernotes.app

import android.app.Application
import com.freshernotes.app.di.ApplicationScope
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.notes.NoteGenerator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FresherApp : Application() {
    @Inject lateinit var notes: NoteGenerator
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var engines: Engines

    /** The LLM holds ~2-3 GB; give it back when the system is short of memory. */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_COMPLETE) {
            appScope.launch { engines.releaseAll() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Finish note generation that was interrupted (app killed after "wrap up").
        appScope.launch { runCatching { notes.generatePending() } }
    }
}
