package io.github.locngu.beforeyoubuy.feature.settings

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupInspection
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupPreview
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupProblem
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupRepository
import io.github.locngu.beforeyoubuy.core.domain.backup.StagedBackup
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Where a backup goes to and comes from: the files the user picked with the system's file pickers (no permission). */
interface BackupStreams {
    fun openOutput(target: String): OutputStream?

    fun openInput(source: String): InputStream?
}

internal class ContentResolverBackupStreams(private val context: Context) : BackupStreams {
    override fun openOutput(target: String): OutputStream? =
        context.contentResolver.openOutputStream(target.toUri(), "wt")

    override fun openInput(source: String): InputStream? = context.contentResolver.openInputStream(source.toUri())
}

@Module
@InstallIn(SingletonComponent::class)
internal object BackupStreamsModule {
    @Provides
    fun provideBackupStreams(@ApplicationContext context: Context): BackupStreams =
        ContentResolverBackupStreams(context)
}

/** What the backup rows of Settings show (brief §9, ADR-0055). */
sealed interface BackupUiState {
    data object Idle : BackupUiState

    /** Reading or writing a file; the rows are off so a second tap does nothing. */
    data object Working : BackupUiState

    /** A readable backup was picked: show what it holds and ask before it replaces everything. */
    data class Preview(val preview: BackupPreview) : BackupUiState
}

/** One-off messages of the backup flow, shown as a snackbar. */
sealed interface BackupEvent {
    data object Exported : BackupEvent

    data object ExportFailed : BackupEvent

    data class ImportRejected(val problem: BackupProblem) : BackupEvent

    /** The file couldn't be read (the picker's file went away). */
    data object ImportUnreadable : BackupEvent

    data object Imported : BackupEvent

    data object ImportFailed : BackupEvent
}

/**
 * Export and import (#27, ADR-0055). Export writes to the file picked with `CreateDocument`; import reads the picked
 * file completely and checks it ([BackupRepository.inspect]), shows [BackupUiState.Preview] and replaces the data only
 * after [confirmReplace]. Until then nothing in the app changes.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val streams: BackupStreams,
    private val clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    private val events = Channel<BackupEvent>(Channel.BUFFERED)
    val backupEvents: Flow<BackupEvent> = events.receiveAsFlow()

    private var staged: StagedBackup? = null

    /** The name the file picker suggests, e.g. `before-you-buy-2026-11-05.zip`. */
    fun suggestedFileName(): String = "before-you-buy-${clock.instant().atZone(clock.zone).toLocalDate()}.zip"

    fun export(target: Uri?, appVersion: String) {
        if (target == null || _uiState.value != BackupUiState.Idle) return
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            val event = try {
                val out = streams.openOutput(target.toString()) ?: throw IOException("Can't open the file")
                out.use { backups.export(it, appVersion) }
                BackupEvent.Exported
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                BackupEvent.ExportFailed
            }
            _uiState.value = BackupUiState.Idle
            events.send(event)
        }
    }

    fun startImport(source: Uri?) {
        if (source == null || _uiState.value != BackupUiState.Idle) return
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            val input = try {
                streams.openInput(source.toString())
            } catch (failed: Exception) {
                null
            }
            if (input == null) {
                _uiState.value = BackupUiState.Idle
                events.send(BackupEvent.ImportUnreadable)
                return@launch
            }
            when (val result = input.use { backups.inspect(it) }) {
                is BackupInspection.Valid -> {
                    staged = result.backup
                    _uiState.value = BackupUiState.Preview(result.backup.preview)
                }

                is BackupInspection.Rejected -> {
                    _uiState.value = BackupUiState.Idle
                    events.send(BackupEvent.ImportRejected(result.problem))
                }
            }
        }
    }

    /** The user confirmed "Replace all". */
    fun confirmReplace() {
        val backup = staged ?: return
        if (_uiState.value !is BackupUiState.Preview) return
        _uiState.value = BackupUiState.Working
        viewModelScope.launch {
            val restored = backup.restore()
            staged = null
            backup.discard()
            _uiState.value = BackupUiState.Idle
            events.send(if (restored) BackupEvent.Imported else BackupEvent.ImportFailed)
        }
    }

    fun cancelImport() {
        if (_uiState.value !is BackupUiState.Preview) return
        staged?.discard()
        staged = null
        _uiState.value = BackupUiState.Idle
    }

    override fun onCleared() {
        staged?.discard()
    }
}
