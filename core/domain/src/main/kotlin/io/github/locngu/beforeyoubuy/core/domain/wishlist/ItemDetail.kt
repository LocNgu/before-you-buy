package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isParked
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.core.domain.pool.progressOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What item detail shows about an active [item] (brief §6.3, ADR-0013).
 *
 * @property daysLeft as in [WishlistEntry.daysLeft].
 * @property wantedForDays calendar days since the item was added (ADR-0018); 0 on the day it was added.
 * @property reflected the required reflection answers are there; if not, detail asks for them.
 * @property linkProminent the shop link may show as a normal button. False while cooling off and for a parked want:
 *   browsing during the
 *   delay defeats it, so the link is only in the overflow menu then (ADR-0013).
 * @property imageProminent the photo may show large. While cooling off it's a small thumbnail to tap (ADR-0013).
 * @property recheckOn as in [WishlistEntry.recheckOn]: the date a "Wait" decision set, while the item waits for it.
 * @property savings what is set aside for a want (brief §4.6); null for a need, which is never saved for (ADR-0044).
 * @property funMoneySetUp money can be set aside; without fun money, detail offers to set it up instead.
 */
data class ItemDetail(
    val item: Item,
    val reflection: Reflection?,
    val stage: Stage,
    val daysLeft: Int,
    val wantedForDays: Int,
    val reflected: Boolean,
    val linkProminent: Boolean,
    val imageProminent: Boolean,
    val recheckOn: LocalDate? = null,
    val savings: SavingsProgress? = null,
    val funMoneySetUp: Boolean = false
)

/**
 * Item detail for an active [item] at [now] in [zone], with the reflection answers given so far and the [savings]
 * (as in [wishlistOf]). Purchased and rejected items have no stage and throw.
 */
fun itemDetailOf(item: Item, reflection: Reflection?, savings: Savings, now: Instant, zone: ZoneId): ItemDetail {
    val stage = stageOf(item, reflection, savings.fundingOf(item), now)
    return ItemDetail(
        item = item,
        reflection = reflection,
        stage = stage,
        daysLeft = daysLeft(item, now, zone),
        wantedForDays = wantedForDays(item, now, zone),
        // A need skips reflection (ADR-0008), so it's never asked for one.
        reflected = isReflected(item, reflection),
        // A parked want isn't one the user chose, so its shop link and photo stay quiet too (ADR-0013, ADR-0049).
        linkProminent = isCooledOff(item, now) && !item.isParked,
        imageProminent = isCooledOff(item, now) && !item.isParked,
        recheckOn = recheckOnOf(item, stage, now, zone),
        savings = savings.progressOf(item),
        funMoneySetUp = savings.setUp
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
