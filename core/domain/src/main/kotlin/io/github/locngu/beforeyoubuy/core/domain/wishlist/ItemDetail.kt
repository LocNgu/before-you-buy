package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What item detail shows about an active [item] (brief §6.3, ADR-0013).
 *
 * @property daysLeft as in [WishlistEntry.daysLeft].
 * @property wantedForDays calendar days since the item was added (ADR-0018); 0 on the day it was added.
 * @property reflected the required reflection answers are there; if not, detail asks for them.
 * @property linkProminent the shop link may show as a normal button. False while cooling off: browsing during the
 *   delay defeats it, so the link is only in the overflow menu then (ADR-0013).
 */
data class ItemDetail(
    val item: Item,
    val reflection: Reflection?,
    val stage: Stage,
    val daysLeft: Int,
    val wantedForDays: Int,
    val reflected: Boolean,
    val linkProminent: Boolean
)

/**
 * Item detail for an active [item] at [now] in [zone], with the reflection answers given so far. Like [wishlistOf],
 * every item counts as funded until savings exist (M2). Purchased and rejected items have no stage and throw.
 */
fun itemDetailOf(item: Item, reflection: Reflection?, now: Instant, zone: ZoneId): ItemDetail {
    val stage = stageOf(item, reflection, item.price, now)
    return ItemDetail(
        item = item,
        reflection = reflection,
        stage = stage,
        daysLeft = daysLeft(item, now, zone),
        wantedForDays = wantedForDays(item, now, zone),
        // A need skips reflection (ADR-0008), so it's never asked for one.
        reflected = isReflected(item, reflection),
        linkProminent = isCooledOff(item, now)
    )
}

/** Calendar days in [zone] from the day [item] was added to the day of [now] (ADR-0018); never negative. */
fun wantedForDays(item: Item, now: Instant, zone: ZoneId): Int {
    val days = ChronoUnit.DAYS.between(item.addedAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    return maxOf(0L, days).toInt()
}

/**
 * The address to open in a browser for a stored [link], or null if it shouldn't be opened (it can still be copied).
 * `http` and `https` links open with the scheme in lower case (Android matches schemes case-sensitively); a link
 * without a scheme (`shop.example/camera`) gets `https://`.
 * Any other scheme (`intent:`, `file:`, `javascript:`, …) never opens: a shared text decides what the link is.
 */
fun webLink(link: String): String? {
    val trimmed = link.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val scheme = SCHEME.find(trimmed)?.groupValues?.get(1)?.lowercase()
    return when {
        scheme == null -> "https://$trimmed"
        scheme == "http" || scheme == "https" -> scheme + trimmed.substring(scheme.length)
        else -> null
    }
}

// A URI scheme (RFC 3986) and its ":". A host with a port (`shop.example:8080/x`) has a digit after the ":", so
// it isn't taken for a scheme.
private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(?!\\d)")

/** Whether item detail offers "Mark as need": only for wants (ADR-0008). */
val Item.canMarkAsNeed: Boolean get() = kind == ItemKind.WANT
