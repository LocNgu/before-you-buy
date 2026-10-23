package io.github.locngu.beforeyoubuy.feature.money

import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Credits the monthly fun money that is due (brief §4.5): at app start and in the daily worker. Running it twice, or
 * both at once, never credits a month twice ([PoolRepository.accrue]).
 */
class PoolAccrual @Inject constructor(private val pool: PoolRepository, private val clock: Clock) {
    suspend fun run(): Int = pool.accrue(clock.instant(), clock.zone)
}
