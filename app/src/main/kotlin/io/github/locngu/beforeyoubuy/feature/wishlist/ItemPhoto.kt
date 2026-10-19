package io.github.locngu.beforeyoubuy.feature.wishlist

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An item's stored photo, decoded off the main thread at about [maxSizePx] on its longer side (ADR-0038). Null while
 * it loads, and if the file is gone or unreadable: show nothing for it then, not an empty frame.
 */
@Composable
internal fun rememberPhoto(file: File, maxSizePx: Int): ImageBitmap? {
    val bitmap by produceState(PhotoCache.cached(file, maxSizePx), file, maxSizePx) {
        // Set on every (re)start: a new file must never keep showing the previous one's bitmap.
        value = PhotoCache.cached(file, maxSizePx) ?: withContext(Dispatchers.IO) { PhotoCache.load(file, maxSizePx) }
    }
    return bitmap
}

@Composable
internal fun ItemPhoto(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) = Image(bitmap, contentDescription, modifier, contentScale = contentScale)

/**
 * Decoded photos by file and size. A stored photo never changes (a new photo gets a new name), so entries never go
 * stale; the cache only keeps scrolling and going back from decoding the same photo again.
 */
internal object PhotoCache {
    // About 1/8 of the app's heap, counted in KB.
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    fun cached(file: File, maxSizePx: Int): ImageBitmap? = cache.get(key(file, maxSizePx))

    /** Decodes [file] (blocking; call it off the main thread) and caches it. Null if it can't be read. */
    fun load(file: File, maxSizePx: Int): ImageBitmap? {
        cached(file, maxSizePx)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSizePx) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap() ?: return null
        cache.put(key(file, maxSizePx), bitmap)
        return bitmap
    }

    private fun key(file: File, maxSizePx: Int) = "${file.path}@$maxSizePx"
}
