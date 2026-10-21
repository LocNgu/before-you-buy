package io.github.locngu.beforeyoubuy.notifications

import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationKind
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationLogRepository
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationPlan
import io.github.locngu.beforeyoubuy.core.domain.notifications.planNotifications
import io.github.locngu.beforeyoubuy.core.domain.notifications.recordSent
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The daily check (brief §8): plans with [planNotifications], records what it sends, then shows it. Run by
 * [NotificationWorker]. Kinds the system wouldn't show (no permission, channel off) count as switched off, so nothing
 * is recorded as sent that the user never saw.
 */
@Singleton
class NotificationCheck @Inject constructor(
    private val items: ItemRepository,
    private val log: NotificationLogRepository,
    private val preferences: PreferencesRepository,
    private val notifier: Notifier,
    private val clock: Clock
) {
    // Two runs at once (the worker and a debug trigger) would both see nothing sent today.
    private val mutex = Mutex()

    /** Returns what was shown, or null. */
    suspend fun run(): NotificationPlan? = mutex.withLock {
        val now = clock.instant()
        val zone = clock.zone
        val wanted = preferences.preferences.first().notifications
        val prefs = NotificationPrefs(
            waitingPeriods = wanted.waitingPeriods && notifier.canShow(NotificationKind.WAITING_PERIOD_OVER),
            reflectionReminders = wanted.reflectionReminders && notifier.canShow(NotificationKind.REFLECTION_NUDGE)
        )
        val notified = log.notified()
        val plan = planNotifications(
            items = items.observeActive().first(),
            reflections = items.observeReflections().first(),
            notified = notified,
            now = now,
            zone = zone,
            prefs = prefs
        ) ?: return null
        // Recorded first: a failure to record must not lead to the same notification every day.
        log.store(recordSent(plan, notified, now))
        notifier.show(plan, now.atZone(zone).toLocalDate())
        plan
    }
}
