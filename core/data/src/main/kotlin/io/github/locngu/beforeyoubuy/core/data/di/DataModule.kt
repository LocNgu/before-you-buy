package io.github.locngu.beforeyoubuy.core.data.di

import android.content.Context
import android.content.res.Resources
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.data.item.RoomItemRepository
import io.github.locngu.beforeyoubuy.core.data.settings.DataStoreSettingsRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.SettingsRepository
import javax.inject.Singleton

/**
 * Binds the domain's repository interfaces to their implementations. Singletons: one database and one DataStore per
 * process (two DataStores on the same file corrupt it).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideItemRepository(@ApplicationContext context: Context): ItemRepository =
        RoomItemRepository(BeforeYouBuyDatabase.create(context))

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE) },
            // The device's region decides the default currency. Not Locale.getDefault(): a per-app language
            // replaces it, often without a region.
            locale = { Resources.getSystem().configuration.locales[0] }
        )

    private const val SETTINGS_FILE = "settings"
}
