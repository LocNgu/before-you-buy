package io.github.locngu.beforeyoubuy.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/** The licence the app's code is under (`LICENSE.md`): source available, never "open source" (ADR-0004). */
const val LICENCE_URL = "https://polyformproject.org/licenses/noncommercial/1.0.0/"

/** The source repository. */
const val SOURCE_URL = "https://github.com/LocNgu/before-you-buy"

/** Android 13+ has a per-app language page in the system settings (`android:localeConfig`); older versions don't. */
fun hasPerAppLanguageSettings(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

/** The system pages Settings links to; other apps open them, so the app needs no permission for any (ADR-0003). */
object SettingsIntents {
    /** The app's notification page: the permission (Android 13+), the app's switch and every channel. */
    fun appNotifications(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** One channel's page ([io.github.locngu.beforeyoubuy.notifications.Notifier.channelOf]). */
    fun channel(context: Context, channelId: String): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    /** The per-app language page (Android 13+), which lists the languages in `locales_config.xml`. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun appLanguage(context: Context): Intent =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
