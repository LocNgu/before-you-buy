package dev.placeholder.beforeyoubuy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The only source of "now" (ADR-0018). Tests use a fixed or fake [Clock] instead. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideClock(): Clock = SystemZoneClock
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
