package io.github.locngu.beforeyoubuy.core.data.backup

import io.github.locngu.beforeyoubuy.core.data.db.AllocationEntity
import io.github.locngu.beforeyoubuy.core.data.db.AllowanceEntity
import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.db.DecisionEntity
import io.github.locngu.beforeyoubuy.core.data.db.ItemEntity
import io.github.locngu.beforeyoubuy.core.data.db.ItemNotificationsEntity
import io.github.locngu.beforeyoubuy.core.data.db.PoolEntryEntity
import io.github.locngu.beforeyoubuy.core.data.db.ReflectionEntity
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupInspection
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupProblem
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class RoomBackupRepositoryTest : DatabaseTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private val prefs = FakePreferences()
    private val now = Instant.parse("2026-11-05T08:00:00Z")
    private val photo = "0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
    private val otherPhoto = "7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg"
    private val images by lazy { folder.newFolder("images") }
    private val staging by lazy { folder.newFolder("staging") }

    private val repository by lazy {
        RoomBackupRepository(
            database,
            prefs,
            images,
            staging,
            Clock.fixed(now, ZoneOffset.UTC),
            kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    private fun item(id: Long, name: String = "Item $id", imageRef: String? = null, slot: Int? = null) = ItemEntity(
        id = id, name = name, priceMinor = 12_345, currency = "EUR", addedAt = 1_000, coolingEndsAt = 2_000,
        kind = "WANT", status = "ACTIVE", link = "https://example.com/$id", imageRef = imageRef, category = "Tech",
        notes = "Ümlaut \"quoted\" ✓", targetDate = "2026-12-24", recheckAt = 3_000, prioritySlot = slot,
        markedAsNeedAt = null, coolingOffDays = 30, waitedDays = 2, waitingSince = 1_500
    )

    private fun populate() = runBlocking {
        database.policyDao().update(
            database.policyDao().get()!!.copy(currency = "EUR", coolingOffRules = "{\"x\":1}", prioritySlots = 4)
        )
        val dao = database.backupDao()
        dao.insertItems(listOf(item(1, imageRef = photo, slot = 1), item(2, imageRef = otherPhoto), item(3)))
        dao.insertReflections(
            listOf(ReflectionEntity(1, "why", "WEEKLY", "[\"a\",\"b\"]", "p", "alt", "r", "now", "NEW"))
        )
        dao.insertDecisions(
            listOf(DecisionEntity(7, 2, "REJECT", 4_000, "APP", 11_500, "EUR", "early", 5_000, "not now"))
        )
        dao.insertNotifications(listOf(ItemNotificationsEntity(1, 6_000, null)))
        dao.insertAllowance(
            AllowanceEntity(
                monthlyMinor = 5_000,
                currency = "EUR",
                accrualDay = 1,
                startDate = "2026-10-01",
                wantsNeedFunding = false
            )
        )
        dao.insertPoolEntries(
            listOf(
                PoolEntryEntity(1, "ACCRUAL", 5_000, "EUR", 100, "2026-10", null, null),
                PoolEntryEntity(2, "PURCHASE", -3_000, "EUR", 200, null, 2, "bought")
            )
        )
        dao.insertAllocations(listOf(AllocationEntity(1, 1, 2_000, "EUR", 300)))
        File(images, photo).writeBytes(byteArrayOf(1, 2, 3))
        File(images, otherPhoto).writeBytes(byteArrayOf(4, 5))
        runBlocking {
            prefs.setNotificationPrefs(NotificationPrefs(waitingPeriods = false, reflectionReminders = true))
            prefs.setOnboardingComplete(true)
            prefs.setNotificationPermissionAsked(true)
        }
    }

    private class State(
        val policy: Any?,
        val items: List<ItemEntity>,
        val reflections: List<ReflectionEntity>,
        val decisions: List<DecisionEntity>,
        val notifications: List<ItemNotificationsEntity>,
        val allowance: AllowanceEntity?,
        val poolEntries: List<PoolEntryEntity>,
        val allocations: List<AllocationEntity>
    ) {
        override fun equals(other: Any?) = other is State && toString() == other.toString()

        override fun hashCode() = toString().hashCode()

        override fun toString() =
            listOf(policy, items, reflections, decisions, notifications, allowance, poolEntries, allocations)
                .joinToString("\n")
    }

    private fun state() = runBlocking {
        State(
            database.policyDao().get(),
            database.itemDao().getAll().sortedBy { it.id },
            database.reflectionDao().getAll().sortedBy { it.itemId },
            database.decisionDao().getAll(),
            database.notificationDao().getAll().sortedBy { it.itemId },
            database.poolDao().getAllowance(),
            database.poolDao().getEntries().sortedBy { it.id },
            database.poolDao().getAllocations().sortedBy { it.id }
        )
    }

    private fun export(): ByteArray = runBlocking {
        ByteArrayOutputStream().also { repository.export(it, "1.2.3") }.toByteArray()
    }

    private fun inspect(bytes: ByteArray) = runBlocking { repository.inspect(ByteArrayInputStream(bytes)) }

    private fun wipe() {
        execSql("DELETE FROM items")
        execSql("DELETE FROM pool_entries")
        execSql("DELETE FROM allowance")
        execSql("UPDATE policy SET currency = 'USD', cooling_off_rules = NULL, priority_slots = 3")
        images.listFiles()!!.forEach { it.delete() }
        runBlocking {
            prefs.setNotificationPrefs(NotificationPrefs())
            prefs.setOnboardingComplete(false)
            prefs.setNotificationPermissionAsked(false)
        }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun valid(): BackupInspection.Valid = assertIs<BackupInspection.Valid>(inspect(export()))

    @Test
    fun `export then wipe then import gives the identical state, photos and settings`() {
        populate()
        val before = state()
        val bytes = export()
        wipe()
        assertTrue(state() != before)

        val staged = assertIs<BackupInspection.Valid>(inspect(bytes)).backup
        assertTrue(runBlocking { staged.restore() })

        assertEquals(before, state())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(images, photo).readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File(images, otherPhoto).readBytes())
        val restored = prefs.current()
        assertEquals(NotificationPrefs(waitingPeriods = false, reflectionReminders = true), restored.notifications)
        assertTrue(restored.onboardingComplete)
        // The permission belongs to the phone, not to the backup.
        assertFalse(restored.notificationPermissionAsked)
        assertEquals(0, staging.listFiles()!!.size)
    }

    @Test
    fun `restoring over existing data replaces it and keeps ids usable`() {
        populate()
        val bytes = export()
        runBlocking { database.backupDao().insertItems(listOf(item(99, "Newer"))) }

        assertTrue(runBlocking { assertIs<BackupInspection.Valid>(inspect(bytes)).backup.restore() })

        assertEquals(listOf(1L, 2L, 3L), runBlocking { database.itemDao().getAll() }.map { it.id }.sorted())
    }

    @Test
    fun `the preview counts items, decisions and photos`() {
        populate()
        val preview = valid().backup.preview
        assertEquals(3, preview.items)
        assertEquals(1, preview.decisions)
        assertEquals(2, preview.photos)
        assertEquals("1.2.3", preview.appVersion)
        assertEquals(now, preview.exportedAt)
    }

    @Test
    fun `an empty app exports and imports`() {
        val before = state()
        assertTrue(runBlocking { valid().backup.restore() })
        assertEquals(before, state())
    }

    @Test
    fun `a file from a newer version is refused`() {
        val future = """{"formatVersion":99,"somethingNew":[1,2]}""".toByteArray()
        assertRejected(BackupProblem.NEWER_VERSION, zip("backup.json" to future))
    }

    @Test
    fun `things that are not backups are refused`() {
        assertRejected(BackupProblem.NOT_A_BACKUP, "hello".toByteArray())
        assertRejected(BackupProblem.NOT_A_BACKUP, zip("other.txt" to "x".toByteArray()))
        assertRejected(BackupProblem.NOT_A_BACKUP, zip("backup.json" to "not json".toByteArray()))
        assertRejected(BackupProblem.NOT_A_BACKUP, zip("backup.json" to "{}".toByteArray()))
    }

    @Test
    fun `a cut-off file is refused and nothing changes`() {
        populate()
        val before = state()
        val bytes = export()
        assertIs<BackupInspection.Rejected>(inspect(bytes.copyOf(bytes.size / 2)))
        assertEquals(before, state())
        assertEquals(0, staging.listFiles()!!.size)
    }

    @Test
    fun `a stream that fails half-way is refused`() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("disk went away")
        }
        val result = runBlocking { repository.inspect(failing) }
        assertEquals(BackupProblem.DAMAGED, assertIs<BackupInspection.Rejected>(result).problem)
    }

    @Test
    fun `rows that point to missing items make a damaged file`() {
        populate()
        val json = zipJson(export()).replace("\"itemId\":1,\"why\"", "\"itemId\":404,\"why\"")
        assertRejected(BackupProblem.DAMAGED, zip("backup.json" to json.toByteArray()))
    }

    @Test
    fun `a failed restore leaves the existing data and photos as they were`() {
        populate()
        val bytes = export()
        val newPhoto = "11111111-2222-3333-4444-555555555555.jpg"
        // A different backup: one item with a photo that is not here yet.
        val other = zipJson(bytes).replace(photo, newPhoto)
        val staged = assertIs<BackupInspection.Valid>(
            inspect(zip("backup.json" to other.toByteArray(), "images/$newPhoto" to byteArrayOf(9)))
        ).backup
        execSql(
            "CREATE TRIGGER fail_decisions BEFORE INSERT ON decisions BEGIN SELECT RAISE(ABORT, 'no'); END"
        )
        val before = state()

        assertFalse(runBlocking { staged.restore() })

        assertEquals(before, state())
        assertFalse(File(images, newPhoto).exists())
        assertTrue(File(images, photo).exists())
    }

    @Test
    fun `discard removes the temporary copy`() {
        val staged = valid().backup
        assertTrue(staging.listFiles()!!.isNotEmpty())
        staged.discard()
        staged.discard()
        assertEquals(0, staging.listFiles()!!.size)
    }

    @Test
    fun `only the app's own photo names are taken from a file`() {
        populate()
        val json = zipJson(export())
        val staged = assertIs<BackupInspection.Valid>(
            inspect(
                zip(
                    "backup.json" to json.toByteArray(),
                    "images/../evil.jpg" to byteArrayOf(1),
                    "images/$photo" to byteArrayOf(8)
                )
            )
        ).backup
        assertEquals(1, staged.preview.photos)
        assertTrue(runBlocking { staged.restore() })
        assertFalse(File(folder.root, "evil.jpg").exists())
    }

    @Test
    fun `an item that names no photo in the file has none and the item is kept`() {
        populate()
        val json = zipJson(export())
        val staged = assertIs<BackupInspection.Valid>(inspect(zip("backup.json" to json.toByteArray()))).backup
        wipe()
        assertTrue(runBlocking { staged.restore() })
        assertNotNull(runBlocking { database.itemDao().getById(1) })
        assertFalse(File(images, photo).exists())
    }

    private fun zipJson(bytes: ByteArray): String = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        generateSequence { z.nextEntry }.first { it.name == "backup.json" }
        z.readBytes().toString(Charsets.UTF_8)
    }

    private fun assertRejected(problem: BackupProblem, bytes: ByteArray) {
        assertEquals(problem, assertIs<BackupInspection.Rejected>(inspect(bytes)).problem)
        assertEquals(0, staging.listFiles()!!.size)
    }
}

private class FakePreferences : PreferencesRepository {
    private val state = MutableStateFlow(AppPreferences())
    override val preferences = state

    fun current() = state.value

    override suspend fun setNotificationPrefs(prefs: NotificationPrefs) {
        state.value = state.value.copy(notifications = prefs)
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        state.value = state.value.copy(onboardingComplete = complete)
    }

    override suspend fun setNotificationPermissionAsked(asked: Boolean) {
        state.value = state.value.copy(notificationPermissionAsked = asked)
    }
}

private inline fun <reified T : Any> assertIs(value: Any?): T {
    assertTrue("Expected ${T::class.simpleName}, was $value", value is T)
    return value as T
}
