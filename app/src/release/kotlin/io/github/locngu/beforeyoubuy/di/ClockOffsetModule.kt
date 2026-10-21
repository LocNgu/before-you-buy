package io.github.locngu.beforeyoubuy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds always run on the system's time. */
@Module
@InstallIn(SingletonComponent::class)
object ClockOffsetModule {
    @Provides
    fun provideClockOffset(): ClockOffset = ClockOffset.None
}
