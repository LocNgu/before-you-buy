package io.github.locngu.beforeyoubuy.feature.money

import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.savingsOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The money set aside per active want and whether wants need it ([savingsOf]), for every screen that shows a stage
 * or the fun money: they all agree on what is set aside (brief §4.6, ADR-0046).
 */
fun observeSavings(items: ItemRepository, pool: PoolRepository): Flow<Savings> =
    combine(pool.allowance, pool.allocations, items.observeActive(), ::savingsOf).distinctUntilChanged()
