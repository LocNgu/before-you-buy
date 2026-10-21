package io.github.locngu.beforeyoubuy.notifications

import android.content.Context
import android.content.Intent
import io.github.locngu.beforeyoubuy.MainActivity
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId

/** Where a tap on a notification, or its "Still want it", leads in the app. */
enum class NotificationTarget {
    /** A digest: the items are all on the wishlist. */
    WISHLIST,

    /** One item's detail. */
    ITEM,

    /** "Still want it": the decision screen. */
    DECISION,

    /** A reflection nudge: the questions. */
    REFLECTION
}

/** A tap on a notification, read from the intent that opened [MainActivity]. [itemId] is null for [NotificationTarget.WISHLIST]. */
data class NotificationTap(val target: NotificationTarget, val itemId: ItemId?, val notificationId: Int)

/** "Let it go" on [notificationId], about [itemId]. */
data class LetItGoRequest(val itemId: ItemId, val notificationId: Int)

/** The explicit intents behind notification taps and actions, and reading them back. */
internal object NotificationIntents {
    const val ACTION_OPEN = "io.github.locngu.beforeyoubuy.action.OPEN_FROM_NOTIFICATION"
    const val ACTION_LET_IT_GO = "io.github.locngu.beforeyoubuy.action.LET_IT_GO"
    private const val EXTRA_TARGET = "target"
    private const val EXTRA_ITEM_ID = "item_id"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"

    /** Opens [MainActivity] at [target], bringing an open app forward instead of starting a second one. */
    fun open(context: Context, target: NotificationTarget, itemId: ItemId?, notificationId: Int): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_TARGET, target.name)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            .apply { if (itemId != null) putExtra(EXTRA_ITEM_ID, itemId.value) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Goes to [LetItGoReceiver]; nothing opens. */
    fun letItGo(context: Context, itemId: ItemId, notificationId: Int): Intent =
        Intent(context, LetItGoReceiver::class.java)
            .setAction(ACTION_LET_IT_GO)
            .putExtra(EXTRA_ITEM_ID, itemId.value)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)

    /** The tap [intent] carries, or null if it isn't one (a launcher start) or is incomplete. */
    fun tapOf(intent: Intent?): NotificationTap? {
        if (intent?.action != ACTION_OPEN) return null
        val target = intent.getStringExtra(EXTRA_TARGET)
            ?.let { name -> NotificationTarget.entries.find { it.name == name } } ?: return null
        val itemId = intent.itemId()
        if (target != NotificationTarget.WISHLIST && itemId == null) return null
        return NotificationTap(target, itemId, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    fun letItGoOf(intent: Intent?): LetItGoRequest? {
        if (intent?.action != ACTION_LET_IT_GO) return null
        val itemId = intent.itemId() ?: return null
        return LetItGoRequest(itemId, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    private fun Intent.itemId(): ItemId? =
        if (hasExtra(EXTRA_ITEM_ID)) ItemId(getLongExtra(EXTRA_ITEM_ID, 0)).takeIf { it != ItemId.NEW } else null
}
