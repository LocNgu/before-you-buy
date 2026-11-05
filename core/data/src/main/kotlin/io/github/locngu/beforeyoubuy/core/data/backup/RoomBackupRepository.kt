package io.github.locngu.beforeyoubuy.core.data.backup

import androidx.room.withTransaction
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.data.image.IMAGE_REF
import io.github.locngu.beforeyoubuy.core.domain.backup.BACKUP_FORMAT_VERSION
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupInspection
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupPreview
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupProblem
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupRepository
import io.github.locngu.beforeyoubuy.core.domain.backup.StagedBackup
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Export and import (#27, ADR-0055): one zip with `backup.json` ([BackupDto]) and the photos the items name, as
 * `images/<ref>.jpg`. Photos are already stripped of metadata (ADR-0038), so they are exported as they are.
 *
 * Importing reads the whole file into [stagingRoot] and checks it before anything is touched; [restore][Staged.restore]
 * then writes the photos and, in one transaction, every row. The settings follow the commit: they are the one part
 * outside the database, and a failed write there loses a switch, not data.
 */
internal class RoomBackupRepository(
    private val database: BeforeYouBuyDatabase,
    private val preferences: PreferencesRepository,
    private val imagesDirectory: File,
    private val stagingRoot: File,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : BackupRepository {
    override suspend fun export(out: OutputStream, appVersion: String) {
        val (dto, photos) = withContext(io) { snapshot(appVersion) }
        withContext(io) {
            val zip = ZipOutputStream(out)
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(JSON.encodeToString(BackupDto.serializer(), dto).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (ref in photos) {
                val file = File(imagesDirectory, ref)
                // A photo that vanished meanwhile is skipped: its item then shows no photo, as it does anyway.
                if (!file.isFile) continue
                zip.putNextEntry(ZipEntry("$IMAGES_PREFIX$ref"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            // Writes the zip's directory without closing [out], which belongs to the caller.
            zip.finish()
            out.flush()
        }
    }

    private suspend fun snapshot(appVersion: String): Pair<BackupDto, Set<String>> {
        // Read outside the transaction: a DataStore that is slow to answer must not hold the database.
        val prefs = preferences.preferences.first()
        return database.withTransaction { snapshotRows(appVersion, prefs) }
    }

    private suspend fun snapshotRows(appVersion: String, prefs: AppPreferences): Pair<BackupDto, Set<String>> {
        val items = database.itemDao().getAll()
        val dto = BackupDto(
            formatVersion = BACKUP_FORMAT_VERSION,
            exportedAt = clock.millis(),
            appVersion = appVersion,
            settings = SettingsDto(
                notifyWaitingPeriods = prefs.notifications.waitingPeriods,
                notifyReflectionReminders = prefs.notifications.reflectionReminders,
                onboardingComplete = prefs.onboardingComplete
            ),
            policy = checkNotNull(database.policyDao().get()) { "The policy row is created on every open" }.toDto(),
            items = items.map { it.toDto() },
            reflections = database.reflectionDao().getAll().map { it.toDto() },
            decisions = database.decisionDao().getAll().map { it.toDto() },
            itemNotifications = database.notificationDao().getAll().map { it.toDto() },
            allowance = database.poolDao().getAllowance()?.toDto(),
            poolEntries = database.poolDao().getEntries().map { it.toDto() },
            allocations = database.poolDao().getAllocations().map { it.toDto() }
        )
        val photos = items.mapNotNullTo(linkedSetOf()) { it.imageRef?.takeIf(IMAGE_REF::matches) }
        return dto to photos
    }

    override suspend fun inspect(input: InputStream): BackupInspection = withContext(io) {
        val directory = File(stagingRoot, UUID.randomUUID().toString())
        try {
            val read = read(input, directory)
            if (read is BackupInspection.Rejected) directory.deleteRecursively()
            read
        } catch (cancelled: CancellationException) {
            directory.deleteRecursively()
            throw cancelled
        } catch (failed: Exception) {
            // A stream that breaks half-way, a zip that is cut off or any surprise: nothing was touched.
            directory.deleteRecursively()
            BackupInspection.Rejected(BackupProblem.DAMAGED)
        }
    }

    private fun read(input: InputStream, directory: File): BackupInspection {
        val photosDirectory = File(directory, IMAGES_DIRECTORY).also { it.mkdirs() }
        var json: ByteArray? = null
        val photos = linkedSetOf<String>()
        try {
            ZipInputStream(input).use { zip ->
                var entries = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return BackupInspection.Rejected(BackupProblem.NOT_A_BACKUP)
                    when {
                        entry.isDirectory -> Unit

                        entry.name == JSON_ENTRY -> json = zip.readLimited(MAX_JSON_BYTES)
                            ?: return BackupInspection.Rejected(BackupProblem.NOT_A_BACKUP)

                        entry.name.startsWith(IMAGES_PREFIX) -> {
                            // Only names the store gives out: nothing in the file can pick a path.
                            val ref = entry.name.removePrefix(IMAGES_PREFIX)
                            if (!IMAGE_REF.matches(ref)) continue
                            val bytes = zip.readLimited(MAX_PHOTO_BYTES)
                                ?: return BackupInspection.Rejected(BackupProblem.DAMAGED)
                            File(photosDirectory, ref).writeBytes(bytes)
                            photos += ref
                        }
                    }
                }
            }
        } catch (notZip: java.util.zip.ZipException) {
            return BackupInspection.Rejected(BackupProblem.NOT_A_BACKUP)
        }
        val text = json?.toString(Charsets.UTF_8) ?: return BackupInspection.Rejected(BackupProblem.NOT_A_BACKUP)
        val dto = parse(text) ?: return BackupInspection.Rejected(problemOf(text))
        if (!dto.isConsistent()) return BackupInspection.Rejected(BackupProblem.DAMAGED)
        val kept = photos.filterTo(linkedSetOf()) { ref -> dto.items.any { it.imageRef == ref } }
        return BackupInspection.Valid(Staged(dto, directory, kept))
    }

    /** Null for a file that isn't a readable backup of a version this app knows. */
    private fun parse(text: String): BackupDto? = try {
        val header = JSON.decodeFromString(BackupHeader.serializer(), text)
        if (header.formatVersion !in
            1..BACKUP_FORMAT_VERSION
        ) {
            null
        } else {
            JSON.decodeFromString(BackupDto.serializer(), text)
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun problemOf(text: String): BackupProblem {
        val version = try {
            JSON.decodeFromString(BackupHeader.serializer(), text).formatVersion
        } catch (_: SerializationException) {
            return BackupProblem.NOT_A_BACKUP
        } catch (_: IllegalArgumentException) {
            return BackupProblem.NOT_A_BACKUP
        }
        return when {
            version > BACKUP_FORMAT_VERSION -> BackupProblem.NEWER_VERSION
            version < 1 -> BackupProblem.NOT_A_BACKUP
            else -> BackupProblem.DAMAGED
        }
    }

    /** The checks the database would make on insert, made first so a bad file is rejected before anything changes. */
    private fun BackupDto.isConsistent(): Boolean {
        val itemIds = items.map { it.id }.toSet()
        return itemIds.size == items.size &&
            reflections.all { it.itemId in itemIds } &&
            reflections.map { it.itemId }.toSet().size == reflections.size &&
            decisions.all { it.itemId in itemIds } &&
            decisions.map { it.id }.toSet().size == decisions.size &&
            itemNotifications.all { it.itemId in itemIds } &&
            itemNotifications.map { it.itemId }.toSet().size == itemNotifications.size &&
            allocations.all { it.itemId in itemIds } &&
            allocations.map { it.id }.toSet().size == allocations.size &&
            poolEntries.all { it.itemId == null || it.itemId in itemIds } &&
            poolEntries.map { it.id }.toSet().size == poolEntries.size &&
            poolEntries.mapNotNull { it.accrualMonth }.let { it.toSet().size == it.size } &&
            policy.prioritySlots in 1..5 &&
            // Every stored amount is in the policy's currency (data.md); a file that says otherwise was edited.
            items.all { it.currency == policy.currency } &&
            poolEntries.all { it.currency == policy.currency } &&
            allocations.all { it.currency == policy.currency } &&
            (allowance == null || allowance.currency == policy.currency)
    }

    private inner class Staged(
        private val dto: BackupDto,
        private val directory: File,
        private val photos: Set<String>
    ) : StagedBackup {
        override val preview = BackupPreview(
            exportedAt = Instant.ofEpochMilli(dto.exportedAt),
            appVersion = dto.appVersion,
            items = dto.items.size,
            decisions = dto.decisions.size,
            photos = photos.size
        )

        override suspend fun restore(): Boolean = withContext(io) {
            val copied = mutableListOf<File>()
            try {
                imagesDirectory.mkdirs()
                for (ref in photos) {
                    val target = File(imagesDirectory, ref)
                    // The same name is the same photo (a uuid); one that is there already stays.
                    if (target.exists()) continue
                    val partial = File(imagesDirectory, "$ref.partial")
                    File(directory, "$IMAGES_DIRECTORY/$ref").copyTo(partial, overwrite = true)
                    if (!partial.renameTo(target)) {
                        partial.delete()
                        throw IOException("Can't store $ref")
                    }
                    copied += target
                }
                database.withTransaction { replaceAllRows() }
            } catch (cancelled: CancellationException) {
                copied.forEach { it.delete() }
                throw cancelled
            } catch (failed: Exception) {
                // Nothing was committed: take back the photos this call added.
                copied.forEach { it.delete() }
                return@withContext false
            }
            runCatching {
                preferences.setNotificationPrefs(
                    NotificationPrefs(dto.settings.notifyWaitingPeriods, dto.settings.notifyReflectionReminders)
                )
                preferences.setOnboardingComplete(dto.settings.onboardingComplete)
            }
            discard()
            true
        }

        private suspend fun replaceAllRows() {
            val dao = database.backupDao()
            dao.deleteItems()
            dao.deletePoolEntries()
            dao.deleteAllowance()
            check(dao.updatePolicy(dto.policy.toEntity()) == 1) { "The policy row is created on every open" }
            dao.insertItems(dto.items.map { it.toEntity() })
            dao.insertReflections(dto.reflections.map { it.toEntity() })
            dao.insertDecisions(dto.decisions.map { it.toEntity() })
            dao.insertNotifications(dto.itemNotifications.map { it.toEntity() })
            dao.insertPoolEntries(dto.poolEntries.map { it.toEntity() })
            dao.insertAllocations(dto.allocations.map { it.toEntity() })
            dto.allowance?.let { dao.insertAllowance(it.toEntity()) }
        }

        override fun discard() {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val JSON_ENTRY = "backup.json"
        const val IMAGES_DIRECTORY = "images"
        const val IMAGES_PREFIX = "images/"
        const val MAX_ENTRIES = 100_000
        const val MAX_JSON_BYTES = 64 * 1024 * 1024
        const val MAX_PHOTO_BYTES = 32 * 1024 * 1024

        // Unknown keys are skipped: a later app version may add fields that this one doesn't need (the format
        // version rises only when that isn't safe).
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** At most [limit] bytes of this entry, or null if it is longer: a zip bomb is not a backup. */
private fun ZipInputStream.readLimited(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        if (out.size() + n > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private const val BUFFER_BYTES = 8 * 1024
