package dev.placeholder.beforeyoubuy

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
open class BeforeYouBuyApplication :
    Application(),
    Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        onAppStart()
    }

    /**
     * All app-start work (scheduling workers, notification channels, …) goes here and nowhere else,
     * so Robolectric tests can switch it off with a test Application (docs/architecture.md, Pitfalls).
     */
    protected open fun onAppStart() = Unit

    // WorkManager initializes on first use with this (its start-up initializer is removed in the
    // manifest). Hilt's worker factory goes here once there are workers.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
