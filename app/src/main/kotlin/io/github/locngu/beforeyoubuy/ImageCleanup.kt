package io.github.locngu.beforeyoubuy

import io.github.locngu.beforeyoubuy.core.domain.item.IMAGE_SWEEP_GRACE
import io.github.locngu.beforeyoubuy.core.domain.item.ItemImageStore
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Deletes item photos no item names any more (ADR-0038): replaced or removed photos, ones picked in an edit that
 * was never saved, later those of deleted items. Photos written in the last [IMAGE_SWEEP_GRACE] are kept, since an
 * open edit may still save them. Runs at app start.
 */
class ImageCleanup @Inject constructor(
    private val items: ItemRepository,
    private val images: ItemImageStore,
    private val clock: Clock
) {
    /** Returns the refs deleted. */
    suspend fun run(): Set<String> =
        images.deleteUnreferenced(items.imageRefs(), clock.instant().minus(IMAGE_SWEEP_GRACE))
}
