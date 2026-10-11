package io.github.locngu.beforeyoubuy.buildlogic

/**
 * The app's `versionName` and `versionCode`. Both come from one source: the release tag `vX.Y.Z`, with
 * `versionCode = X * 10000 + Y * 100 + Z` (ADR-0029).
 */
internal data class AppVersion(val name: String, val code: Int) {
    companion object {
        /** Builds without a tag (local, PRs). Its code is below every release's, so a release installs over it. */
        val DEV = AppVersion("0.0.0-dev", 1)

        // Google Play's upper bound for versionCode.
        private const val MAX_VERSION_CODE = 2_100_000_000L

        // MINOR and PATCH have two decimal digits in the versionCode, so they stop at 99. No leading zeros.
        private val TAG = Regex("""v(0|[1-9]\d{0,5})\.(0|[1-9]\d?)\.(0|[1-9]\d?)""")

        /** The version for release tag [tag], or [DEV] if there is none. Fails on a tag that isn't `vX.Y.Z`. */
        fun fromTag(tag: String?): AppVersion {
            if (tag.isNullOrBlank()) return DEV
            val match =
                requireNotNull(TAG.matchEntire(tag)) {
                    "Release tag '$tag' isn't vMAJOR.MINOR.PATCH (e.g. v1.2.3, MINOR and PATCH 0–99, no " +
                        "pre-release suffix). See docs/releasing.md."
                }
            val (major, minor, patch) = match.destructured.toList().map(String::toLong)
            val code = major * 10_000 + minor * 100 + patch
            // Above DEV's code, so every release ranks above every dev build.
            require(code in DEV.code + 1..MAX_VERSION_CODE) {
                "Release tag '$tag' gives versionCode $code, outside ${DEV.code + 1}..$MAX_VERSION_CODE."
            }
            return AppVersion("$major.$minor.$patch", code.toInt())
        }
    }
}
