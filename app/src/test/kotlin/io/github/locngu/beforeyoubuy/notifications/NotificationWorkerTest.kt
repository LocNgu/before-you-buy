package io.github.locngu.beforeyoubuy.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.locngu.beforeyoubuy.core.domain.notifications.untilNextDailyCheck
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun scheduled(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(NotificationWorker.DAILY_WORK).get()

    @Test
    fun `the check is scheduled once a day, first at the next daily check time`() {
        val now = Instant.parse("2026-10-15T08:00:00Z")

        NotificationWorker.schedule(context, now, ZoneOffset.UTC)

        val work = scheduled().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(Duration.ofDays(1).toMillis(), work.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NotificationWorker.FLEX.toMillis(), work.periodicityInfo?.flexIntervalMillis)
        assertEquals(untilNextDailyCheck(now, ZoneOffset.UTC).toMillis(), work.initialDelayMillis)
    }

    @Test
    fun `starting the app again keeps the scheduled check instead of moving it`() {
        NotificationWorker.schedule(context, Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC)
        val first = scheduled().single().id

        NotificationWorker.schedule(context, Instant.parse("2026-10-15T09:30:00Z"), ZoneOffset.UTC)

        assertEquals(first, scheduled().single().id)
    }

    @Test
    fun `the worker runs the check in the real app`() = runTest {
        val worker = TestListenableWorkerBuilder<NotificationWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun `the manifest asks for notifications but never for exact alarms`() {
        val info: PackageInfo =
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()

        assertTrue(Manifest.permission.POST_NOTIFICATIONS in requested)
        assertFalse(Manifest.permission.SCHEDULE_EXACT_ALARM in requested)
        assertFalse(Manifest.permission.USE_EXACT_ALARM in requested)
    }
}
