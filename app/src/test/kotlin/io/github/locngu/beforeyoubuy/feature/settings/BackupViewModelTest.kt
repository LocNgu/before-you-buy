package io.github.locngu.beforeyoubuy.feature.settings

import android.net.Uri
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupInspection
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupPreview
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupProblem
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupRepository
import io.github.locngu.beforeyoubuy.core.domain.backup.StagedBackup
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {
    private val now = Instant.parse("2026-11-05T08:00:00Z")
    private val preview = BackupPreview(now, "1.0.0", items = 12, decisions = 30, photos = 5)
    private val file = Uri.parse("content://documents/backup.zip")

    private val staged = object : StagedBackup {
        override val preview = this@BackupViewModelTest.preview
        var restoreResult = true
        var restored = 0
        var discarded = 0

        override suspend fun restore(): Boolean {
            restored++
            return restoreResult
        }

        override fun discard() {
            discarded++
        }
    }

    private var inspection: BackupInspection = BackupInspection.Valid(staged)
    private var exportFails = false
    private val exported = ByteArrayOutputStream()

    private val backups = object : BackupRepository {
        override suspend fun export(out: OutputStream, appVersion: String) {
            if (exportFails) throw IOException("full")
            out.write(appVersion.toByteArray())
        }

        override suspend fun inspect(input: InputStream): BackupInspection = inspection
    }

    private var input: InputStream? = ByteArrayInputStream(byteArrayOf(1))
    private val streams = object : BackupStreams {
        override fun openOutput(target: String): OutputStream? = exported

        override fun openInput(source: String): InputStream? = input
    }

    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BackupViewModel(backups, streams, MutableClock(now))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the suggested name carries the date`() {
        assertEquals("before-you-buy-2026-11-05.zip", viewModel.suggestedFileName())
    }

    @Test
    fun `export writes the file and says so`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.export(file, "1.2.3")

        assertEquals("1.2.3", exported.toString())
        assertEquals(BackupEvent.Exported, viewModel.backupEvents.first())
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `a failed export says so, and a cancelled picker does nothing`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.export(null, "1.2.3")
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)

        exportFails = true
        viewModel.export(file, "1.2.3")
        assertEquals(BackupEvent.ExportFailed, viewModel.backupEvents.first())
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `a readable file shows its preview and changes nothing until confirmed`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel.startImport(file)

            assertEquals(BackupUiState.Preview(preview), viewModel.uiState.value)
            assertEquals(0, staged.restored)
        }

    @Test
    fun `confirming replaces, reports and cleans up`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.startImport(file)
        viewModel.confirmReplace()

        assertEquals(1, staged.restored)
        assertEquals(BackupEvent.Imported, viewModel.backupEvents.first())
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
        assertTrue(staged.discarded > 0)
    }

    @Test
    fun `a restore that fails is reported`() = runTest(UnconfinedTestDispatcher()) {
        staged.restoreResult = false
        viewModel.startImport(file)
        viewModel.confirmReplace()

        assertEquals(BackupEvent.ImportFailed, viewModel.backupEvents.first())
    }

    @Test
    fun `cancelling discards the file and restores nothing`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.startImport(file)
        viewModel.cancelImport()

        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
        assertEquals(0, staged.restored)
        assertEquals(1, staged.discarded)
        viewModel.confirmReplace()
        assertEquals(0, staged.restored)
    }

    @Test
    fun `a rejected file says why and shows no preview`() = runTest(UnconfinedTestDispatcher()) {
        inspection = BackupInspection.Rejected(BackupProblem.NEWER_VERSION)
        viewModel.startImport(file)

        assertEquals(BackupEvent.ImportRejected(BackupProblem.NEWER_VERSION), viewModel.backupEvents.first())
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `a file that cannot be opened is reported`() = runTest(UnconfinedTestDispatcher()) {
        input = null
        viewModel.startImport(file)

        assertEquals(BackupEvent.ImportUnreadable, viewModel.backupEvents.first())
        assertFalse(viewModel.uiState.value is BackupUiState.Preview)
    }

    @Test
    fun `clearing the view model discards a file still waiting for the answer`() {
        viewModel.startImport(file)
        val onCleared = BackupViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        onCleared.invoke(viewModel)

        assertEquals(1, staged.discarded)
    }
}
