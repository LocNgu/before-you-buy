package io.github.locngu.beforeyoubuy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The only source of "now" (ADR-0018). Tests use a fixed or fake [Clock] instead. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /** [offset] is [ClockOffset.None] in release builds; debug builds can move the app's time forward (#20). */
    @Provides
    fun provideClock(offset: ClockOffset): Clock = if (offset == ClockOffset.None) SystemZoneClock else OffsetClock(offset)
}

/**
 * How far the app's time is ahead of the system's. Bound per build type: release builds have [None]; debug builds
 * read a manual-testing setting, so waiting periods can end without waiting (`DebugClockOffset`).
 */
fun interface ClockOffset {
    fun get(): Duration

    object None : ClockOffset {
        override fun get(): Duration = Duration.ZERO
    }
}

/**
 * System time in the device's *current* time zone. Unlike `Clock.systemDefaultZone()`, the zone is
 * read on every call, so long-lived holders get the right calendar date after the user travels.
 */
internal object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}

/** [SystemZoneClock] moved by [offset], read on every call so a change applies at once. */
private class OffsetClock(private val offset: ClockOffset, private val zone: ZoneId? = null) : Clock() {
    override fun getZone(): ZoneId = zone ?: SystemZoneClock.zone

    override fun withZone(zone: ZoneId): Clock = OffsetClock(offset, zone)

    override fun instant(): Instant = Instant.now().plus(offset.get())
}
