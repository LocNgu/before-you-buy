package io.github.locngu.beforeyoubuy.core.domain.item

import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Item photos (brief §4.1, ADR-0038): copies of images picked with the Photo Picker, kept in app storage and named
 * by [Item.imageRef]. Implemented in `:core:data`.
 *
 * A photo file is never deleted when an item changes: [deleteUnreferenced] removes the files no item names any more
 * (a replaced or removed photo, an edit that wasn't saved, later a deleted item).
 */
interface ItemImageStore {
    /**
     * Copies the image at [source] (a `content:` or `file:` URI) into app storage: turned upright, scaled down to at
     * most [MAX_IMAGE_SIZE_PX] on its longer side and saved as JPEG without the original's metadata (no location).
     * Returns the ref to store in [Item.imageRef], or null if [source] can't be read as an image.
     */
    suspend fun import(source: String): String?

    /** Where the photo [ref] is stored, or null if [ref] isn't a name this store gives out. Doesn't touch the disk. */
    fun fileFor(ref: String): File?

    /**
     * Deletes stored photos that no ref in [referenced] names, last written before [writtenBefore] (see
     * [unreferencedImages]). Returns the refs deleted.
     */
    suspend fun deleteUnreferenced(referenced: Set<String>, writtenBefore: Instant): Set<String>
}

/** The longer side of a stored photo, in pixels: sharp on a phone screen, a few hundred KB as JPEG. */
const val MAX_IMAGE_SIZE_PX = 1600

/**
 * How old an unreferenced photo must be before the sweep deletes it: a photo picked in an edit that isn't saved yet
 * is unreferenced too, and must survive until the edit is saved or discarded.
 */
val IMAGE_SWEEP_GRACE: Duration = Duration.ofHours(1)

/**
 * The stored photos to delete: those in [stored] (ref → when it was written) that no ref in [referenced] names and
 * that were written before [writtenBefore]. Anything referenced is kept, however old.
 */
fun unreferencedImages(stored: Map<String, Instant>, referenced: Set<String>, writtenBefore: Instant): Set<String> =
    stored.filter { (ref, writtenAt) -> ref !in referenced && writtenAt < writtenBefore }.keys
