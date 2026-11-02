package com.nuvio.app.features.luxo

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationRequest
import java.util.concurrent.TimeUnit

/** Checks «نبهني لما ينزل» movies and followed actors, and posts notifications. */
class LuxoReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching {
            LuxoPrefsStorage.initialize(applicationContext)
            EpisodeReleaseNotificationPlatform.initialize(applicationContext)
            LuxoReminders.check { id, title, body, deepLink, backdrop ->
                EpisodeReleaseNotificationPlatform.showTestNotification(
                    EpisodeReleaseNotificationRequest(
                        requestId = id,
                        notificationTitle = title,
                        notificationBody = body,
                        releaseDateIso = "",
                        deepLinkUrl = deepLink,
                        backdropUrl = backdrop,
                    ),
                )
            }
        }
        return Result.success()
    }
}

actual fun luxoScheduleReminderChecks() {
    val context = LuxoPrefsStorage.appContext ?: return
    runCatching {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "luxo-reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LuxoReminderWorker>(12, TimeUnit.HOURS).build(),
        )
    }
}
