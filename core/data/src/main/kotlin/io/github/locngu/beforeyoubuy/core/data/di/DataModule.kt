package io.github.locngu.beforeyoubuy.core.data.di

import android.content.Context
import android.content.res.Resources
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.data.item.RoomItemRepository
import io.github.locngu.beforeyoubuy.core.data.settings.DataStorePreferencesRepository
import io.github.locngu.beforeyoubuy.core.data.settings.RoomPolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.defaultCurrency
import javax.inject.Singleton

/**
 * Binds the domain's repository interfaces to their implementations. Singletons: one database and one DataStore per
 * process (two DataStores on the same file corrupt it).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeforeYouBuyDatabase =
        BeforeYouBuyDatabase.create(context) {
            // The device's region decides the first currency. Not Locale.getDefault(): a per-app language replaces
            // it, often without a region.
            defaultCurrency(Resources.getSystem().configuration.locales[0])
        }

    @Provides
    @Singleton
    fun provideItemRepository(database: BeforeYouBuyDatabase): ItemRepository = RoomItemRepository(database)

    @Provides
    @Singleton
    fun providePolicyRepository(database: BeforeYouBuyDatabase): PolicyRepository = RoomPolicyRepository(database)

    @Provides
    @Singleton
    fun providePreferencesRepository(@ApplicationContext context: Context): PreferencesRepository =
        DataStorePreferencesRepository(
            DataStorePreferencesRepository.createDataStore { context.preferencesDataStoreFile(PREFERENCES_FILE) }
        )

    private const val PREFERENCES_FILE = "preferences"
}
