package io.github.locngu.beforeyoubuy.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.locngu.beforeyoubuy.debug.DebugClockOffset

/** Debug builds can move the app's time forward for manual testing (`DebugNotificationsReceiver`). */
@Module
@InstallIn(SingletonComponent::class)
object ClockOffsetModule {
    @Provides
    fun provideClockOffset(@ApplicationContext context: Context): ClockOffset = DebugClockOffset(context)
}
