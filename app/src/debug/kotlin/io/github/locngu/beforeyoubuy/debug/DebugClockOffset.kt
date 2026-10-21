package io.github.locngu.beforeyoubuy.debug

import android.content.Context
import androidx.core.content.edit
import io.github.locngu.beforeyoubuy.di.ClockOffset
import java.time.Duration

/**
 * Debug builds only: how far the app's time is ahead of the system's, set with `DebugNotificationsReceiver`. Kept in
 * its own preferences file so it survives restarts; zero until set.
 */
class DebugClockOffset(context: Context) : ClockOffset {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun get(): Duration = Duration.ofMillis(preferences.getLong(KEY, 0))

    fun set(offset: Duration) = preferences.edit { putLong(KEY, offset.toMillis()) }

    private companion object {
        const val FILE = "debug_clock"
        const val KEY = "offset_millis"
    }
}
