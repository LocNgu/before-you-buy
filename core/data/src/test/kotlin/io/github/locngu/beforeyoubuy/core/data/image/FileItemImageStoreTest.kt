package io.github.locngu.beforeyoubuy.core.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class FileItemImageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory by lazy { File(folder.root, "images") }

    private fun store(scheduler: TestCoroutineScheduler) = FileItemImageStore(
        ApplicationProvider.getApplicationContext<Context>().contentResolver,
        directory,
        StandardTestDispatcher(scheduler)
    )

    /** A JPEG of [width] × [height] in the temporary folder, as a `file:` URI. */
    private fun picture(width: Int, height: Int, exif: Map<String, String> = emptyMap()): String {
        val file = folder.newFile()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (exif.isNotEmpty()) {
            ExifInterface(file.path).apply {
                exif.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
        }
        return Uri.fromFile(file).toString()
    }

    private fun size(ref: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(directory, ref).path, options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun `a large photo is scaled down to fit and stored under a new ref`() = runTest {
        val images = store(testScheduler)

        val ref = images.import(picture(4000, 1000))!!

        assertEquals(File(directory, ref), images.fileFor(ref))
        assertEquals(1600 to 400, size(ref))
    }

    @Test
    fun `a small photo keeps its size`() = runTest {
        val ref = store(testScheduler).import(picture(300, 200))!!

        assertEquals(300 to 200, size(ref))
    }

    @Test
    fun `a rotated photo is stored upright, without its location`() = runTest {
        val source = picture(
            400,
            200,
            mapOf(
                ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString(),
                ExifInterface.TAG_GPS_LATITUDE to "52/1,31/1,0/1",
                ExifInterface.TAG_GPS_LATITUDE_REF to "N"
            )
        )

        val ref = store(testScheduler).import(source)!!

        assertEquals(200 to 400, size(ref))
        val stored = ExifInterface(File(directory, ref).path)
        assertNull(stored.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            stored.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        )
    }

    @Test
    fun `a transparent image is stored on white, not black`() = runTest {
        val png = folder.newFile("logo.png")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) // fully transparent
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val ref = store(testScheduler).import(Uri.fromFile(png).toString())!!

        val corner = BitmapFactory.decodeFile(File(directory, ref).path).getPixel(0, 0)
        assertTrue("corner was ${Integer.toHexString(corner)}", Color.red(corner) > 240 && Color.blue(corner) > 240)
    }

    @Test
    fun `something that isn't an image gives no ref and leaves nothing behind`() = runTest {
        val text = folder.newFile().apply { writeText("not an image") }
        val images = store(testScheduler)

        assertNull(images.import(Uri.fromFile(text).toString()))
        assertNull(images.import(Uri.fromFile(File(folder.root, "missing.jpg")).toString()))
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test
    fun `only names this store gives out map to files`() {
        val images = FileItemImageStore(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            directory
        )

        assertNotNull(images.fileFor("0f8fad5b-d9cb-469f-a165-70867728950e.jpg"))
        assertNull(images.fileFor("../databases/app.db"))
        assertNull(images.fileFor("0f8fad5b-d9cb-469f-a165-70867728950e.jpg/../x"))
        assertNull(images.fileFor(""))
    }

    @Test
    fun `the sweep deletes old unreferenced files and keeps the rest`() = runTest {
        val cutoff = Instant.parse("2026-10-19T10:00:00Z")
        directory.mkdirs()
        fun file(name: String, writtenAt: Instant) =
            File(directory, name).apply { writeText("x") }.also { it.setLastModified(writtenAt.toEpochMilli()) }
        val kept = file("a.jpg", cutoff.minusSeconds(3600))
        val orphan = file("b.jpg", cutoff.minusSeconds(3600))
        val fresh = file("c.jpg", cutoff.plusSeconds(60))
        val partial = file("d.jpg.partial", cutoff.minusSeconds(3600))

        val deleted = store(testScheduler).deleteUnreferenced(setOf("a.jpg"), cutoff)

        assertEquals(setOf("b.jpg", "d.jpg.partial"), deleted)
        assertTrue(kept.exists())
        assertTrue(fresh.exists())
        assertFalse(orphan.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun `the sweep does nothing before the first photo`() = runTest {
        assertEquals(emptySet<String>(), store(testScheduler).deleteUnreferenced(emptySet(), Instant.now()))
    }
}
