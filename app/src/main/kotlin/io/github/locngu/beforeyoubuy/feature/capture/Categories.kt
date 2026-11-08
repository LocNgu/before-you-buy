package io.github.locngu.beforeyoubuy.feature.capture

import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.wishlist.distinctCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The categories used so far on active and history items, de-duplicated ignoring case (#83). Quick add and edit
 * suggest them while a category is typed; there is no category list to manage.
 */
fun observeCategories(items: ItemRepository): Flow<List<String>> = combine(
    items.observeActive(),
    items.observeHistory(ItemStatus.PURCHASED),
    items.observeHistory(ItemStatus.REJECTED)
) { active, bought, rejected ->
    distinctCategories(active.map { it.category } + bought.map { it.item.category } + rejected.map { it.item.category })
}
