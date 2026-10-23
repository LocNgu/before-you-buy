package io.github.locngu.beforeyoubuy.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.locngu.beforeyoubuy.core.domain.notifications.untilNextDailyCheck
import io.github.locngu.beforeyoubuy.feature.money.PoolAccrual
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Runs [NotificationCheck] once a day (brief §8). WorkManager, never exact alarms. It also credits the monthly fun
 * money ([PoolAccrual], brief §4.5), so it arrives on its day even if the app isn't opened.
 */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, NotificationsEntryPoint::class.java)
        // Retried by the next run or app start; it never keeps the notifications from going out.
        runCatching { entryPoint.poolAccrual().run() }
        entryPoint.notificationCheck().run()
        return Result.success()
    }

    companion object {
        const val DAILY_WORK = "daily-notification-check"

        /** The daily window is flexible: the check may run up to this long before the day is up, to save battery. */
        val FLEX: Duration = Duration.ofHours(2)

        /**
         * Schedules the daily check, first around [io.github.locngu.beforeyoubuy.core.domain.notifications.DAILY_CHECK_TIME]
         * after [now]. Keeps a check already scheduled, so starting the app doesn't move it.
         */
        fun schedule(context: Context, now: Instant, zone: ZoneId) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(Duration.ofDays(1), FLEX)
                .setInitialDelay(untilNextDailyCheck(now, zone))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Runs the check once now, outside the schedule (debug builds' manual testing). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<NotificationWorker>().build())
        }
    }
}

/** What the worker and the receiver need from Hilt; neither can be injected directly. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationsEntryPoint {
    fun notificationCheck(): NotificationCheck

    fun letItGo(): LetItGo

    fun poolAccrual(): PoolAccrual
}
