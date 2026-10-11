package io.github.locngu.beforeyoubuy.buildlogic

/** Reads `CHANGELOG.md` ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format). */
internal object Changelog {
    // Link reference definitions at the end of the file, e.g. "[0.1.0]: https://…".
    private val LINK_DEFINITION = Regex("""\[[^]]+]:\s.*""")

    /**
     * The body of the `## [version]` section (the heading may carry a date: `## [1.2.3] - 2026-10-11`), trimmed, up
     * to the next `## ` heading. Null if there is no such section or it's empty.
     */
    fun section(changelog: String, version: String): String? {
        val heading = Regex("""## \[${Regex.escape(version)}](\s.*)?""")
        val lines = changelog.lines()
        val start = lines.indexOfFirst { heading.matches(it.trimEnd()) }
        if (start < 0) return null
        return lines
            .drop(start + 1)
            .takeWhile { !it.startsWith("## ") && !LINK_DEFINITION.matches(it) }
            .joinToString("\n")
            .trim()
            .ifEmpty { null }
    }
}
