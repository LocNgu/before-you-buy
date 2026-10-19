package io.github.locngu.beforeyoubuy

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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
    }

    // WorkManager initializes on first use with this (its start-up initializer is removed in the
    // manifest). Hilt's worker factory goes here once there are workers.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
