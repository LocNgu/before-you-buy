package io.github.locngu.beforeyoubuy.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
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
    fun `the worker credits the fun money in the real app, each month once`() = runTest {
        val worker = TestListenableWorkerBuilder<NotificationWorker>(context).build()
        // Opens the app's database (Room creates it), with no allowance yet: nothing to credit.
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, accrualCount())

        // Set up long ago with the 1st, so months are due whatever today's date is. Raw SQL: the database and its
        // repository are internal to :core:data.
        appDatabase().use {
            it.execSQL(
                "INSERT INTO allowance (id, monthly_minor, currency, accrual_day, start_date) " +
                    "SELECT 1, 30000, currency, 1, '2025-01-01' FROM policy"
            )
        }

        worker.doWork()
        val credited = accrualCount()
        assertTrue("Expected months to be credited, was $credited", credited >= 12)
        worker.doWork()
        assertEquals(credited, accrualCount())
    }

    /** The app's own database file (`BeforeYouBuyDatabase.NAME`, internal to :core:data). */
    private fun appDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath("before-you-buy.db").path,
        null,
        SQLiteDatabase.OPEN_READWRITE
    )

    private fun accrualCount(): Int = appDatabase().use { db ->
        db.rawQuery("SELECT COUNT(DISTINCT accrual_month), COUNT(*) FROM pool_entries WHERE type = 'ACCRUAL'", null)
            .use { cursor ->
                cursor.moveToFirst()
                // Distinct months equal rows: no month was credited twice.
                assertEquals(cursor.getInt(0), cursor.getInt(1))
                cursor.getInt(1)
            }
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
