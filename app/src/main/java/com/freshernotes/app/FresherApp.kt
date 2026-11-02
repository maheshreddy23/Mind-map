package com.freshernotes.app

import android.app.Application
import android.content.Context
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.di.ApplicationScope
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.library.SessionFiler
import com.freshernotes.core.llm.ContextManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FresherApp : Application() {
    @Inject lateinit var filer: SessionFiler
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var sessions: SessionRepository
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
        appScope.launch {
            // The search index is derived data: rebuild it once after a schema change (v2 adds entries).
            val prefs = getSharedPreferences("search_index", Context.MODE_PRIVATE)
            if (prefs.getInt("version", 0) < SEARCH_INDEX_VERSION) {
                runCatching {
                    library.rebuildIndex { id -> ContextManager.transcript(sessions.turns(id)) }
                    prefs.edit().putInt("version", SEARCH_INDEX_VERSION).apply()
                }
            }
            // Finish filing that was interrupted (app killed after "wrap up").
            runCatching { filer.filePending() }
        }
    }

    private companion object {
        const val SEARCH_INDEX_VERSION = 4
    }
}
