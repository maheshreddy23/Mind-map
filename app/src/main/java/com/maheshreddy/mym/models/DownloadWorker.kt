package com.maheshreddy.mym.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.maheshreddy.mym.core.models.Catalog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** Runs one model download in the background with a progress notification (WorkManager foreground work). */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun downloader(): ModelDownloader }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val model = Catalog.byId(id) ?: return Result.failure()
        val downloader = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).downloader()
        runCatching { setForeground(info(model.name, 0)) }
        return try {
            coroutineScope {
                val progress = launch {
                    var last = -1
                    downloader.states.collect { m ->
                        val pct = (m[id]?.fraction?.times(100))?.toInt() ?: return@collect
                        if (pct != last) { last = pct; runCatching { setForeground(info(model.name, pct)) } }
                    }
                }
                downloader.download(model)
                progress.cancel()
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            // Network trouble resumes automatically; a checksum failure (file deleted) is not retried.
            if (e.message?.contains("checksum") == true) Result.failure() else if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        info(Catalog.byId(inputData.getString(KEY_ID).orEmpty())?.name ?: "model", 0)

    private fun info(name: String, pct: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MYM is downloading $name")
            .setContentText("$pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val notifId = 4000 + (inputData.getString(KEY_ID).hashCode() and 0xfff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(notifId, n)
    }

    companion object {
        const val KEY_ID = "model_id"
        const val CHANNEL = "model_downloads"
    }
}
