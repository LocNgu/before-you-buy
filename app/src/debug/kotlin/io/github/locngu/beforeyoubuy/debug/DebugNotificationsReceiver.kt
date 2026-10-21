package io.github.locngu.beforeyoubuy.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.locngu.beforeyoubuy.notifications.NotificationWorker
import java.time.Duration

/**
 * Debug builds only: moves the app's time and runs the daily notification check, so waiting periods can end and
 * notifications appear without waiting (issue #20). From a computer with the device connected:
 *
 * ```
 * adb shell am broadcast -p io.github.locngu.beforeyoubuy -a io.github.locngu.beforeyoubuy.debug.ADVANCE_CLOCK --ei days 7
 * adb shell am broadcast -p io.github.locngu.beforeyoubuy -a io.github.locngu.beforeyoubuy.debug.RUN_NOTIFICATION_CHECK
 * adb shell am broadcast -p io.github.locngu.beforeyoubuy -a io.github.locngu.beforeyoubuy.debug.RESET_CLOCK
 * ```
 *
 * The first moves the time 7 days ahead (default 1) and checks, the second only checks, the third returns to real
 * time. `-p` is needed: Android delivers implicit broadcasts to manifest receivers only when addressed to the app. The
 * whole app follows the moved time (wishlist, decision screen), and so does the one-a-day rule: move a day ahead for
 * the next notification.
 */
class DebugNotificationsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val offset = DebugClockOffset(context)
        when (intent.action) {
            ACTION_ADVANCE_CLOCK -> offset.set(offset.get().plus(Duration.ofDays(intent.getIntExtra(EXTRA_DAYS, 1).toLong())))
            ACTION_RESET_CLOCK -> offset.set(Duration.ZERO)
            ACTION_RUN_CHECK -> Unit
            else -> return
        }
        Log.i(TAG, "The app's time is ${offset.get().toDays()} days ahead")
        if (intent.action != ACTION_RESET_CLOCK) NotificationWorker.runNow(context)
    }

    private companion object {
        const val TAG = "DebugNotifications"
        const val ACTION_RUN_CHECK = "io.github.locngu.beforeyoubuy.debug.RUN_NOTIFICATION_CHECK"
        const val ACTION_ADVANCE_CLOCK = "io.github.locngu.beforeyoubuy.debug.ADVANCE_CLOCK"
        const val ACTION_RESET_CLOCK = "io.github.locngu.beforeyoubuy.debug.RESET_CLOCK"
        const val EXTRA_DAYS = "days"
    }
}
