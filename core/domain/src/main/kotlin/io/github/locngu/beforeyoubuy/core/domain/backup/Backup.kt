package io.github.locngu.beforeyoubuy.core.domain.backup

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * The backup file format (brief §9, ADR-0055): a zip holding `backup.json` and, optionally, `images/<ref>.jpg`.
 * [BACKUP_FORMAT_VERSION] is `formatVersion` in the JSON; a file with a higher one is refused, an older one is read
 * (a change that older apps can't read raises it).
 */
const val BACKUP_FORMAT_VERSION = 1

/** What a backup file holds, for the preview before it replaces anything ("12 items, 30 decisions"). */
data class BackupPreview(
    val exportedAt: Instant,
    val appVersion: String,
    val items: Int,
    val decisions: Int,
    val photos: Int
)

/** Why a file can't be restored. The user's data is untouched in every case. */
enum class BackupProblem {
    /** Not a backup of this app: not a zip, no `backup.json`, or not the JSON of a backup. */
    NOT_A_BACKUP,

    /** Made by a newer version of the app, which this one can't read safely. */
    NEWER_VERSION,

    /** A backup that is cut off or inconsistent (e.g. rows that point to items the file doesn't have). */
    DAMAGED
}

sealed interface BackupInspection {
    /** A readable backup. Restore it with [StagedBackup.restore] or let go of it with [StagedBackup.discard]. */
    class Valid(val backup: StagedBackup) : BackupInspection

    class Rejected(val problem: BackupProblem) : BackupInspection
}

/** A backup file that has been read and checked completely, ready to replace the app's data. */
interface StagedBackup {
    val preview: BackupPreview

    /**
     * Replaces everything the app stores (items, reflections, decisions, notification log, allowance, fun money,
     * allocations, policy, photos and settings) with the backup's, all or nothing: returns false and leaves the
     * existing data as it was if that fails. Not for the notification permission, which belongs to the phone.
     */
    suspend fun restore(): Boolean

    /** Deletes the temporary copy of the file. Safe to call more than once, and after [restore]. */
    fun discard()
}

/** Export and import of the app's data (brief §9). Implemented in `:core:data`. */
interface BackupRepository {
    /**
     * Writes a backup of everything the app stores to [out], read from one consistent snapshot. [appVersion] is
     * recorded in it. Throws `IOException` if [out] can't be written; closes nothing.
     */
    suspend fun export(out: OutputStream, appVersion: String)

    /**
     * Reads [input] completely and checks it. Never touches the app's data; a file that can't be restored is
     * [BackupInspection.Rejected] with the reason, also when [input] fails half-way.
     */
    suspend fun inspect(input: InputStream): BackupInspection
}
