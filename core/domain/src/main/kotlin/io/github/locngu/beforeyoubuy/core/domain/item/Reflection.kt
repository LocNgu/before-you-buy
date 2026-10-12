package io.github.locngu.beforeyoubuy.core.domain.item

/**
 * The user's answers to the reflection questions (brief §4.3, ADR-0009). Every answer may still be missing:
 * reflection happens after capture, a bit at a time.
 *
 * Required for Ready: [why], [usageFrequency] and at least one of [reasonsAgainst]
 * (see [io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected]). The rest is optional.
 */
data class Reflection(
    val why: String? = null,
    val usageFrequency: UsageFrequency? = null,
    val reasonsAgainst: List<String> = emptyList(),
    val problemSolved: String? = null,
    val currentAlternative: String? = null,
    val replaces: String? = null,
    val whyNow: String? = null,
    val upgradeOrNew: UpgradeOrNew? = null
)

/** "How often will you use it?" (brief §4.3). */
enum class UsageFrequency { DAILY, WEEKLY, MONTHLY, RARELY, ONCE }

/** "Upgrade or genuinely new?" (brief §4.3). */
enum class UpgradeOrNew { UPGRADE, NEW }
