package io.github.locngu.beforeyoubuy.core.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationNoticeTest {
    private val allOn = NotificationPrefs(waitingPeriods = true, reflectionReminders = true)
    private val allOff = NotificationPrefs(waitingPeriods = false, reflectionReminders = false)

    @Test
    fun `nothing to say when the phone shows everything`() {
        assertEquals(NotificationNotice.None, notificationNoticeOf(allOn, SystemNotifications()))
    }

    @Test
    fun `a blocked app is said once, whatever the channels`() {
        val blocked = SystemNotifications(appAllowed = false, waitingPeriodsChannelOn = false)

        assertEquals(NotificationNotice.AppBlocked, notificationNoticeOf(allOn, blocked))
        assertEquals(
            NotificationNotice.AppBlocked,
            notificationNoticeOf(allOn.copy(waitingPeriods = false), SystemNotifications(appAllowed = false))
        )
    }

    @Test
    fun `nothing to say when every switch is off, even if the phone blocks the app`() {
        assertEquals(NotificationNotice.None, notificationNoticeOf(allOff, SystemNotifications(appAllowed = false)))
        assertEquals(
            NotificationNotice.None,
            notificationNoticeOf(
                allOff,
                SystemNotifications(waitingPeriodsChannelOn = false, reflectionRemindersChannelOn = false)
            )
        )
    }

    @Test
    fun `a channel that is off is named only while its switch is on`() {
        val waitingOff = SystemNotifications(waitingPeriodsChannelOn = false)

        assertEquals(
            NotificationNotice.ChannelsOff(waitingPeriods = true, reflectionReminders = false),
            notificationNoticeOf(allOn, waitingOff)
        )
        assertEquals(NotificationNotice.None, notificationNoticeOf(allOn.copy(waitingPeriods = false), waitingOff))
    }

    @Test
    fun `both channels off are both named`() {
        val bothOff = SystemNotifications(waitingPeriodsChannelOn = false, reflectionRemindersChannelOn = false)

        assertEquals(
            NotificationNotice.ChannelsOff(waitingPeriods = true, reflectionReminders = true),
            notificationNoticeOf(allOn, bothOff)
        )
        assertEquals(
            NotificationNotice.ChannelsOff(waitingPeriods = false, reflectionReminders = true),
            notificationNoticeOf(allOn.copy(waitingPeriods = false), bothOff)
        )
    }
}
