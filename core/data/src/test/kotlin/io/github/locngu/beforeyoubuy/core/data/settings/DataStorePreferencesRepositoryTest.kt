package io.github.locngu.beforeyoubuy.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePreferencesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "preferences.preferences_pb") }

    /** One DataStore per file and process: a second active one on the same file throws. */
    private fun storeIn(scope: CoroutineScope): DataStore<Preferences> =
        DataStorePreferencesRepository.createDataStore(scope) { file }

    private fun TestScope.repository(store: DataStore<Preferences> = storeIn(backgroundScope)) =
        DataStorePreferencesRepository(store)

    @Test
    fun `a fresh install reads defaults`() = runTest {
        assertEquals(AppPreferences(), repository().preferences.first())
    }

    @Test
    fun `stores notification prefs and the onboarding flag`() = runTest {
        val repository = repository()
        val prefs = NotificationPrefs(waitingPeriods = false, reflectionReminders = true)

        repository.setNotificationPrefs(prefs)
        repository.setOnboardingComplete(true)

        assertEquals(AppPreferences(prefs, onboardingComplete = true), repository.preferences.first())
    }

    @Test
    fun `preferences emit on every change`() = runTest {
        val repository = repository()

        repository.preferences.test {
            assertEquals(false, awaitItem().onboardingComplete)
            repository.setOnboardingComplete(true)
            assertEquals(true, awaitItem().onboardingComplete)
        }
    }

    @Test
    fun `a corrupt file is replaced by defaults and stays writable`() = runTest {
        file.writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03))
        val repository = repository()

        assertEquals(AppPreferences(), repository.preferences.first())
        repository.setOnboardingComplete(true)
        assertEquals(true, repository.preferences.first().onboardingComplete)
    }

    @Test
    fun `a failed read is retried without showing defaults, and later changes still arrive`() = runTest {
        val real = storeIn(backgroundScope)
        var failNextRead = true
        val flaky = object : DataStore<Preferences> by real {
            override val data = flow {
                if (failNextRead) {
                    failNextRead = false
                    throw IOException("disk hiccup")
                }
                emitAll(real.data)
            }
        }
        real.edit { it[booleanPreferencesKey("onboarding_complete")] = true }
        val repository = repository(flaky)

        repository.preferences.test {
            // The retry reads the file; no defaults (onboarding not done) before it.
            assertEquals(true, awaitItem().onboardingComplete)
            repository.setNotificationPrefs(NotificationPrefs(waitingPeriods = false))
            assertEquals(false, awaitItem().notifications.waitingPeriods)
        }
    }

    @Test
    fun `preferences survive a restart`() = runTest {
        val firstRun = Job()
        DataStorePreferencesRepository(storeIn(CoroutineScope(Dispatchers.IO + firstRun))).setOnboardingComplete(true)
        firstRun.cancelAndJoin() // Closes the file, like the process ending.

        assertEquals(true, repository().preferences.first().onboardingComplete)
    }
}
