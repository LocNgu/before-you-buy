package dev.placeholder.beforeyoubuy

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
open class BeforeYouBuyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        onAppStart()
    }

    /**
     * All app-start work (scheduling workers, notification channels, …) goes here and nowhere else,
     * so Robolectric tests can switch it off with a test Application (docs/architecture.md, Pitfalls).
     */
    protected open fun onAppStart() = Unit
}
