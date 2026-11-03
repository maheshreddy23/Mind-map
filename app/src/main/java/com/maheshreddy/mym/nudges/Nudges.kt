package com.maheshreddy.mym.nudges

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maheshreddy.mym.MainActivity
import com.maheshreddy.mym.data.ButlerRepository
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.SessionRepository
import com.maheshreddy.mym.data.settings.AppSettings
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.core.library.Nudge
import com.maheshreddy.mym.core.library.NudgeEngine
import com.maheshreddy.mym.core.library.NudgeEntry
import com.maheshreddy.mym.core.library.NudgeInput
import com.maheshreddy.mym.core.library.NudgeTopic
import com.maheshreddy.mym.ui.library.LibraryUi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Today's nudge, computed on the phone from the library (Home card and the daily notification). */
@Singleton
class NudgeService @Inject constructor(
    private val library: LibraryRepository,
    private val sessions: SessionRepository,
    private val butler: ButlerRepository,
    private val settings: SettingsRepository,
) {
    val current: Flow<Nudge?> = combine(
        combine(library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts()) { t, s, c -> Triple(t, s, LibraryUi.tiles(t, s, c)) },
        library.observeOldestFiled(),
        sessions.recentSessions(20),
        butler.nudgeSnoozedUntil,
        settings.settings,
    ) { (topics, _, tiles), oldest, recent, snoozed, prefs -> pick(tiles, topics, oldest, recent, snoozed, prefs) }

    private fun pick(
        tiles: List<com.maheshreddy.mym.ui.library.TopicTile>,
        topics: List<com.maheshreddy.mym.data.db.TopicEntity>,
        oldest: com.maheshreddy.mym.data.db.EntryEntity?,
        recent: List<com.maheshreddy.mym.data.db.SessionEntity>,
        snoozed: Long,
        prefs: AppSettings,
    ): Nudge? {
        val now = System.currentTimeMillis()
        val start = LibraryRepository.startOfDay(now)
        return NudgeEngine.pick(
            NudgeInput(
                now = now, startOfToday = start, snoozedUntil = snoozed,
                topics = tiles.map { NudgeTopic(it.id, it.title, it.openQuestions) },
                oldest = oldest?.let { e -> NudgeEntry(e.id, e.title, topics.firstOrNull { it.id == e.topicId }?.title, e.createdAt) },
                sessionsToday = recent.count { it.startedAt >= start },
                enabled = buildSet {
                    if (prefs.nudgeUnfinished) add(Nudge.Kind.UNFINISHED_TOPIC)
                    if (prefs.nudgeResurface) add(Nudge.Kind.RESURFACE)
                    if (prefs.nudgeDaily) add(Nudge.Kind.DAILY_CAPTURE)
                },
            ),
        )
    }
}

/** Schedules the daily nudge notification at the chosen time, never inside quiet hours. */
@Singleton
class NudgeScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule(prefs: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!(prefs.nudgeUnfinished || prefs.nudgeResurface || prefs.nudgeDaily)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val at = NudgeEngine.notifyAt(prefs.nudgeTimeMin, prefs.quietStartMin, prefs.quietEndMin)
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, at / 60); set(Calendar.MINUTE, at % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val request = PeriodicWorkRequestBuilder<NudgeWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object { const val WORK = "daily-nudge" }
}

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun nudges(): NudgeService
        fun settings(): SettingsRepository
        fun butler(): ButlerRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val prefs = deps.settings().settings.first()
        val cal = Calendar.getInstance()
        if (NudgeEngine.isQuiet(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE), prefs.quietStartMin, prefs.quietEndMin)) return Result.success()
        val nudge = deps.nudges().current.first() ?: return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return Result.success()
        val name = deps.butler().profile.first().displayName
        notify(applicationContext, name, nudge)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "nudges"
        const val EXTRA_TALK_ABOUT = "talk_about"
        const val EXTRA_TALK = "talk"

        fun notify(context: Context, butlerName: String, nudge: Nudge) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Nudges", NotificationManager.IMPORTANCE_DEFAULT))
            val talk = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_TALK, true).putExtra(EXTRA_TALK_ABOUT, nudge.talkAbout)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("$butlerName · today's nudge")
                .setContentText(nudge.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.text))
                .setContentIntent(open)
                .addAction(0, "Talk now", talk)
                .setAutoCancel(true)
                .build()
            nm.notify(7001, n)
        }
    }
}
