package io.github.locngu.beforeyoubuy.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.locngu.beforeyoubuy.notifications.Notifier
import io.github.locngu.beforeyoubuy.notifications.SystemNotificationsReader

/** The notification state Settings reads comes from the same [Notifier] the daily check uses. */
@Module
@InstallIn(SingletonComponent::class)
interface NotificationsModule {
    @Binds
    fun bindSystemNotificationsReader(notifier: Notifier): SystemNotificationsReader
}
