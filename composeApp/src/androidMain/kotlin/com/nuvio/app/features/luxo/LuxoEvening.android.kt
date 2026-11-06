package com.nuvio.app.features.luxo

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationRequest
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** «اقتراح المساء» and «ذكريات المشاهدة»: runs once a day at the chosen hour. */
class LuxoEveningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching {
            LuxoPrefsStorage.initialize(applicationContext)
            EpisodeReleaseNotificationPlatform.initialize(applicationContext)
            LuxoEvening.dueNotifications().forEach { n ->
                EpisodeReleaseNotificationPlatform.showTestNotification(
                    EpisodeReleaseNotificationRequest(
                        requestId = n[0].orEmpty(),
                        notificationTitle = n[1].orEmpty(),
                        notificationBody = n[2].orEmpty(),
                        releaseDateIso = "",
                        deepLinkUrl = n[3].orEmpty(),
                        backdropUrl = n[4],
                    ),
                )
            }
        }
        return Result.success()
    }
}

actual fun luxoScheduleEvening(hour: Int?) {
    val context = LuxoPrefsStorage.appContext ?: return
    runCatching {
        val wm = WorkManager.getInstance(context)
        if (hour == null) { wm.cancelUniqueWork("luxo-evening"); return }
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<LuxoEveningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork("luxo-evening", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
}
