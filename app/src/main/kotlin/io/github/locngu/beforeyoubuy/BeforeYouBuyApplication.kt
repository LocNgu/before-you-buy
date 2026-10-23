package io.github.locngu.beforeyoubuy

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.locngu.beforeyoubuy.feature.money.PoolAccrual
import io.github.locngu.beforeyoubuy.notifications.NotificationWorker
import io.github.locngu.beforeyoubuy.notifications.Notifier
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
open class BeforeYouBuyApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var imageCleanup: ImageCleanup

    @Inject
    lateinit var notifier: Notifier

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var poolAccrual: PoolAccrual

    // Work that outlives every screen; it ends with the process.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        onAppStart()
    }

    /**
     * All app-start work (scheduling workers, notification channels, …) goes here and nowhere else,
     * so Robolectric tests can switch it off with a test Application (docs/architecture.md, Pitfalls).
     */
    protected open fun onAppStart() {
        // Housekeeping only: a failure (e.g. storage unavailable) leaves files for the next start, never crashes.
        appScope.launch { runCatching { imageCleanup.run() } }
        // Months since the last start; the daily worker catches up too if the app isn't opened. A failure is retried
        // by the next start or worker run, which credit the same months.
        appScope.launch { runCatching { poolAccrual.run() } }
        // Channels exist before anything is posted, named in the current language (brief §8).
        notifier.createChannels()
        NotificationWorker.schedule(this, clock.instant(), clock.zone)
    }

    // WorkManager initializes on first use with this (its start-up initializer is removed in the
    // manifest). Workers get their dependencies through Hilt entry points (NotificationsEntryPoint), so the default
    // worker factory is enough.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
