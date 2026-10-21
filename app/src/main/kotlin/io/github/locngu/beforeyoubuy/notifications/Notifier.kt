package io.github.locngu.beforeyoubuy.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationKind
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationPlan
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows a [NotificationPlan] as an Android notification (brief §8, ADR-0014, ADR-0042). One channel per
 * [NotificationKind], so the user can switch each off in the system settings.
 */
@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * Creates the channels, or renames them after a language change. Cheap and safe to repeat; runs at app start and
     * before every check.
     */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_WAITING_PERIODS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_waiting_periods))
                    .setDescription(context.getString(R.string.notification_channel_waiting_periods_description))
                    .build(),
                // Quieter: a reminder, not news.
                NotificationChannelCompat.Builder(CHANNEL_REFLECTION, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notification_channel_reflection))
                    .setDescription(context.getString(R.string.notification_channel_reflection_description))
                    .build()
            )
        )
    }

    /**
     * Whether the system would show a notification of [kind]: the app may notify (the Android 13+ permission and the
     * app's switch) and the kind's channel isn't switched off.
     */
    fun canShow(kind: NotificationKind): Boolean {
        if (!hasPermission() || !manager.areNotificationsEnabled()) return false
        createChannels()
        val channel = manager.getNotificationChannelCompat(channelOf(kind)) ?: return false
        return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /** Shows [plan] as today's notification of its kind and returns its id. Shows nothing without the permission. */
    fun show(plan: NotificationPlan, today: LocalDate): Int? {
        // The explicit check also tells Lint the permission is handled.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        createChannels()
        val id = notificationId(plan.kind, today)
        manager.notify(id, build(plan, id))
        return id
    }

    private fun build(plan: NotificationPlan, id: Int) = NotificationCompat.Builder(context, channelOf(plan.kind))
        .setSmallIcon(R.drawable.ic_notification)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .apply {
            val resources = context.resources
            val names = plan.items.joinToString(", ") { it.item.name }
            val single = plan.items.first()
            when {
                plan.kind == NotificationKind.WAITING_PERIOD_OVER && !plan.isDigest -> {
                    val text = resources.getQuantityString(
                        R.plurals.notification_waiting_over_text,
                        single.wantedForDays,
                        single.item.name,
                        single.wantedForDays
                    )
                    setContentTitle(single.item.name)
                    setContentText(text)
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    setContentIntent(open(NotificationTarget.ITEM, single.item.id, id, ACTION_CONTENT))
                    addAction(
                        0,
                        context.getString(R.string.notification_still_want_it),
                        open(NotificationTarget.DECISION, single.item.id, id, ACTION_STILL_WANT_IT)
                    )
                    addAction(0, context.getString(R.string.notification_let_it_go), letItGo(single.item.id, id))
                }

                plan.kind == NotificationKind.WAITING_PERIOD_OVER -> {
                    setContentTitle(
                        resources.getQuantityString(
                            R.plurals.notification_waiting_over_digest_title,
                            plan.items.size,
                            plan.items.size
                        )
                    )
                    setContentText(names)
                    setStyle(
                        NotificationCompat.InboxStyle().also { style -> plan.items.forEach { style.addLine(it.item.name) } }
                    )
                    setContentIntent(open(NotificationTarget.WISHLIST, null, id, ACTION_CONTENT))
                }

                !plan.isDigest -> {
                    val text = context.getString(R.string.notification_nudge_text, single.item.name)
                    setContentTitle(single.item.name)
                    setContentText(text)
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    setContentIntent(open(NotificationTarget.REFLECTION, single.item.id, id, ACTION_CONTENT))
                }

                else -> {
                    val text = context.getString(R.string.notification_nudge_digest_text, names)
                    setContentTitle(
                        resources.getQuantityString(R.plurals.notification_nudge_digest_title, plan.items.size, plan.items.size)
                    )
                    setContentText(text)
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    setContentIntent(open(NotificationTarget.WISHLIST, null, id, ACTION_CONTENT))
                }
            }
        }
        .build()

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    // Pending intents are told apart by request code, not extras: one per notification and action.
    private fun open(target: NotificationTarget, itemId: ItemId?, notificationId: Int, action: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(notificationId, action),
            NotificationIntents.open(context, target, itemId, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun letItGo(itemId: ItemId, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(notificationId, ACTION_LET_IT_GO),
        NotificationIntents.letItGo(context, itemId, notificationId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL_WAITING_PERIODS = "waiting_periods"
        const val CHANNEL_REFLECTION = "reflection_reminders"

        private const val ACTION_CONTENT = 0
        private const val ACTION_STILL_WANT_IT = 1
        private const val ACTION_LET_IT_GO = 2

        fun channelOf(kind: NotificationKind): String = when (kind) {
            NotificationKind.WAITING_PERIOD_OVER -> CHANNEL_WAITING_PERIODS
            NotificationKind.REFLECTION_NUDGE -> CHANNEL_REFLECTION
        }

        /** One id per kind and day, so a new day's notification doesn't replace yesterday's if it's still there. */
        fun notificationId(kind: NotificationKind, day: LocalDate): Int =
            (day.toEpochDay() * NotificationKind.entries.size + kind.ordinal).toInt()

        private fun requestCode(notificationId: Int, action: Int) = notificationId * 3 + action
    }
}
