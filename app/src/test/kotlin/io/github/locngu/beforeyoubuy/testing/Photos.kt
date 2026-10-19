package io.github.locngu.beforeyoubuy.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import io.github.locngu.beforeyoubuy.core.domain.item.MAX_IMAGE_SIZE_PX
import io.github.locngu.beforeyoubuy.feature.wishlist.PhotoCache
import io.github.locngu.beforeyoubuy.feature.wishlist.THUMBNAIL_SIZE_PX
import java.io.File

/**
 * A stand-in product photo in [directory]: a disc on a plain background, as JPEG. Already decoded into the photo
 * cache at every size the screens use, so a screenshot shows it on the first frame instead of after an async load.
 * Needs `@GraphicsMode(NATIVE)`.
 */
fun testPhoto(directory: File, name: String = "photo.jpg", width: Int = 800, height: Int = 600): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(0xFFDCE4E8.toInt())
        val paint = Paint().apply {
            isAntiAlias = true
            color = 0xFF41728C.toInt()
        }
        drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, paint)
    }
    val file = File(directory, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    listOf(THUMBNAIL_SIZE_PX, MAX_IMAGE_SIZE_PX).forEach { PhotoCache.load(file, it) }
    return file
}
