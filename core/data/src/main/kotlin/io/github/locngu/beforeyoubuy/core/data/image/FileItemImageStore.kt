package io.github.locngu.beforeyoubuy.core.data.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import io.github.locngu.beforeyoubuy.core.domain.item.ItemImageStore
import io.github.locngu.beforeyoubuy.core.domain.item.MAX_IMAGE_SIZE_PX
import io.github.locngu.beforeyoubuy.core.domain.item.unreferencedImages
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Item photos as JPEG files in [directory] (app-private storage, ADR-0038), named `<uuid>.jpg`. Each import decodes
 * the picked image and writes a new file, so the stored photo never carries the original's metadata (location,
 * device) and needs no permission to read the picked URI again.
 */
internal class FileItemImageStore(
    private val contentResolver: ContentResolver,
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ItemImageStore {
    override suspend fun import(source: String): String? = withContext(io) {
        val uri = source.toUri()
        val bitmap = try {
            decodeUpright(uri)?.let(::onWhite)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreadable: Exception) {
            // Whatever another app's provider or the decoder throws (the picker's grant ended, a corrupt or odd
            // file): the user picked something that can't be read, which is a message, not a crash.
            null
        } ?: return@withContext null
        try {
            directory.mkdirs()
            val ref = "${UUID.randomUUID()}.jpg"
            // Written under a temporary name and renamed, so a half-written file never has a ref's name.
            val partial = File(directory, "$ref.partial")
            partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!partial.renameTo(File(directory, ref))) {
                partial.delete()
                return@withContext null
            }
            ref
        } catch (failed: IOException) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    override fun fileFor(ref: String): File? = if (REF.matches(ref)) File(directory, ref) else null

    override suspend fun deleteUnreferenced(referenced: Set<String>, writtenBefore: Instant): Set<String> =
        withContext(io) {
            val files = directory.listFiles().orEmpty().filter { it.isFile }
            // Leftovers of an import that died half-way have no ref; they go like any unreferenced photo.
            val stored = files.associate { it.name to Instant.ofEpochMilli(it.lastModified()) }
            unreferencedImages(stored, referenced, writtenBefore)
                .filterTo(mutableSetOf()) { File(directory, it).delete() }
        }

    /** The image at [uri], scaled down to at most [MAX_IMAGE_SIZE_PX] and turned as its EXIF orientation says. */
    private fun decodeUpright(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Powers of two only get close; the exact scale comes after decoding.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIZE_PX) sample *= 2
        val decoded = open(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = runCatching {
            open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, NO_ROTATION) }
        }.getOrDefault(NO_ROTATION)
        val scale = minOf(1f, MAX_IMAGE_SIZE_PX.toFloat() / max(decoded.width, decoded.height))
        val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
        if (matrix.isIdentity) return decoded
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    /** JPEG has no transparency, which it would write as black: put transparent images on white first. */
    private fun onWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val opaque = createBitmap(bitmap.width, bitmap.height)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap.recycle()
        return opaque
    }

    private fun open(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")

    private companion object {
        const val JPEG_QUALITY = 85
        const val NO_ROTATION = ExifInterface.ORIENTATION_NORMAL
        val REF = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
    }
}

/** The rotation and mirroring that turns an image with EXIF [orientation] upright. */
private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)

        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            postRotate(90f)
            postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            postRotate(-90f)
            postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
    }
}
