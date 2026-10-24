package io.github.locngu.beforeyoubuy.feature.wishlist

import io.github.locngu.beforeyoubuy.core.domain.money.Money

/** Savings actions that do nothing, for screenshots. */
internal object NoSavingsActions : SavingsActions {
    override fun openSetAside() = Unit

    override fun openTakeBack() = Unit

    override fun onSavingsAmountChange(text: String) = Unit

    override fun onSavingsChip(amount: Money) = Unit

    override fun confirmSavings() = Unit

    override fun dismissSavings() = Unit
}
