package io.github.locngu.beforeyoubuy.core.domain.capture

import java.text.BreakIterator
import java.util.Locale

/**
 * What a share from a shop or browser says about an item (brief §6.2), read offline: the app has no `INTERNET`
 * permission (ADR-0003), so the price is never known. Made by [parseSharedText].
 *
 * @property name the product name the shared text or subject gives, null if it gives none.
 * @property url the first `http(s)` link, without tracking parameters; null if there's none.
 */
data class SharedItemDraft(val name: String? = null, val url: String? = null) {
    /**
     * The link's host as people read it ("amazon.de" for `https://www.amazon.de/…`), null without a link. For a
     * fallback name like "amazon.de item" when the share has no name; the app words it, so it can be translated.
     */
    val shop: String? get() = url?.let(::hostOf)?.let(::readableHost)
}

/** Shared names longer than this are cut at a word and end with "…". */
const val MAX_SHARED_NAME_LENGTH = 100

/**
 * Reads a share's text (`EXTRA_TEXT`) and [subject] (`EXTRA_SUBJECT`, else `EXTRA_TITLE`) into a draft:
 *
 * - the first `http(s)://` link in [text] (else in [subject]) is the [SharedItemDraft.url], without tracking
 *   parameters (`utm_*`, `ref`, `tag`, click ids, Amazon's `/ref=…` segment, eBay's share parameters). Amazon's
 *   `/dp/<ASIN>` stays as it is.
 * - what's left of [text] without links and boilerplate ("Check out this deal on Amazon", "Schau dir das an") is the
 *   [SharedItemDraft.name]; if nothing is left, the same from [subject]. A shop's name next to the title ("… | eBay",
 *   "Amazon.de: …") goes too.
 *
 * It never fails: text it can't read gives an empty draft.
 */
fun parseSharedText(text: String, subject: String?): SharedItemDraft {
    val shared = text.take(MAX_INPUT_LENGTH)
    val sharedSubject = subject?.take(MAX_INPUT_LENGTH)
    val url = (firstUrl(shared) ?: sharedSubject?.let(::firstUrl))?.let(::withoutTracking)
    val site = url?.let(::hostOf)?.let(::siteName)
    val name = nameIn(shared, site) ?: sharedSubject?.let { nameIn(it, site) }
    return SharedItemDraft(name = name, url = url)
}

// Enough for any share; a pasted novel is cut before the regexes see it.
private const val MAX_INPUT_LENGTH = 10_000

// `*`, not `+`: a bare "https://" is no link, but it isn't a name either.
private val URL = Regex("""https?://[^\s<>"“”„«»]*""", RegexOption.IGNORE_CASE)
private val HOST = Regex("""^https?://(?:[^/?#@]*@)?([^/?#:]+)""", RegexOption.IGNORE_CASE)

private fun firstUrl(text: String): String? = URL.findAll(text)
    .map { it.value.withoutTrailingPunctuation() }
    .firstOrNull { url -> hostOf(url)?.any(Char::isLetterOrDigit) == true }
    ?.let { "${it.substringBefore("://").lowercase()}://${it.substringAfter("://")}" }

/** "Look: https://x.de/a." → `https://x.de/a`; a closing bracket stays if the link opened it (`/Foo_(bar)`). */
private fun String.withoutTrailingPunctuation(): String {
    // Brackets still open in this[0, end): counted once, updated as the end moves left.
    var round = count { it == '(' } - count { it == ')' }
    var square = count { it == '[' } - count { it == ']' }
    var end = length
    while (end > 0) {
        val unmatched = when (this[end - 1]) {
            ')' -> round < 0
            ']' -> square < 0
            else -> this[end - 1] in ".,;:!?'’…*"
        }
        if (!unmatched) break
        when (this[end - 1]) {
            ')' -> round++
            ']' -> square++
        }
        end--
    }
    return substring(0, end)
}

private fun hostOf(url: String): String? = HOST.find(url)?.groupValues?.get(1)?.lowercase()?.ifEmpty { null }

private fun readableHost(host: String): String = host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")

/**
 * The shop's own name in [host]: `amazon` for `www.amazon.co.uk`, `ebay` for `ebay.de`. Null for names too short to
 * recognise in a title (`a` for `a.co` would match "A - Z Puzzle").
 */
private fun siteName(host: String): String? {
    val labels = readableHost(host).split('.').filter(String::isNotEmpty)
    val secondLevelSuffix = labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL
    val name = if (labels.size < 2) labels.firstOrNull() else labels[labels.size - if (secondLevelSuffix) 3 else 2]
    return name?.takeIf { it.length >= MIN_SITE_NAME_LENGTH }
}

private const val MIN_SITE_NAME_LENGTH = 3

private val SECOND_LEVEL = setOf("co", "com", "org", "net", "ac", "gov", "or", "ne")

// Tracking, affiliate and click ids, never what the page shows. Matched without regard to case.
private val TRACKING_PREFIXES = listOf("utm_", "pd_rd_", "pf_rd_", "_trk")
private val TRACKING_PARAMETERS = setOf(
    "ref", "ref_", "ref_src", "tag", "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid",
    "igshid", "mc_cid", "mc_eid", "_ga", "_gl", "srsltid"
)
private val SITE_TRACKING_PARAMETERS = mapOf(
    "amazon" to setOf(
        "crid", "sprefix", "keywords", "qid", "sr", "dib", "dib_tag", "content-id", "social_share", "starsleft",
        "skiptwisterog", "_encoding", "linkcode", "linkid", "camp", "creative", "creativeasin", "ascsubtag"
    ),
    "ebay" to setOf(
        "mkcid", "mkevt", "mkrid", "campid", "toolid", "customid", "ssspo", "sssrc", "ssuid", "widget_ver", "media"
    )
)
private val AMAZON_REF_SEGMENT = Regex("""/ref=[^/]*""")

private fun withoutTracking(url: String): String {
    val site = hostOf(url)?.let(::siteName)
    // Chrome's "link to highlighted text" (`#:~:text=…`, also after a page's own `#anchor`) isn't part of the address.
    val fragment = url.substringAfter('#', missingDelimiterValue = "").substringBefore(":~:")
    val beforeFragment = url.substringBefore('#')
    var path = beforeFragment.substringBefore('?')
    val query = beforeFragment.substringAfter('?', missingDelimiterValue = "")
    if (site == "amazon") path = AMAZON_REF_SEGMENT.replace(path, "")

    val siteParameters = SITE_TRACKING_PARAMETERS[site].orEmpty()
    val kept = query.split('&').filter { parameter ->
        val name = parameter.substringBefore('=').lowercase()
        name.isNotEmpty() &&
            name !in TRACKING_PARAMETERS &&
            name !in siteParameters &&
            TRACKING_PREFIXES.none(name::startsWith)
    }
    return buildString {
        append(path)
        if (kept.isNotEmpty()) append('?').append(kept.joinToString("&"))
        if (fragment.isNotEmpty()) append('#').append(fragment)
    }
}

// What shop apps and people put around the title. Each must end the line or be followed by punctuation, and the
// noun after "this" is one shops use ("this deal"), so a phrase never eats a product's name ("Schau dir diese Lampe").
private const val SHOP = """[\p{L}\p{N}.]+"""
private const val SHARED_THING = """deal|product|item|offer|find|listing"""
private const val GERMAN_SHARED_THING = """angebot|produkt|artikel|fundstück|teil"""
private val BOILERPLATE = Regex(
    listOf(
        """check (?:this|it) out(?: (?:on|at) $SHOP)?""",
        """check out (?:this|these|that)(?: (?:$SHARED_THING)s?)?(?: (?:on|at|from) $SHOP)?""",
        """(?:look )?(?:at )?what i found(?: (?:on|at) $SHOP)?""",
        """i found (?:this|it)(?: (?:on|at) $SHOP)?""",
        """found (?:this|it) (?:on|at) $SHOP""",
        """(?:have a )?look at this""",
        """(?:schau|sieh|guck) mal,? was ich (?:auf|bei) $SHOP gefunden habe""",
        """(?:schau|sieh|guck)(?: dir)?(?: mal)? (?:das|dies|dieses|diesen|diese)(?: (?:$GERMAN_SHARED_THING))?""" +
            """(?: (?:auf|bei|von) $SHOP)?(?: an)?""",
        """(?:schau|sieh|guck) mal""",
        """(?:das )?(?:habe|hab) ich (?:auf|bei) $SHOP gefunden""",
        """ich (?:habe|hab) (?:das|dies) (?:auf|bei) $SHOP gefunden"""
    ).joinToString("|", prefix = """^(?:""", postfix = """)(?=$|\s*[:!.,;\-–—])[\s:!.,;\-–—]*"""),
    setOf(RegexOption.IGNORE_CASE)
)
private val INVISIBLE = Regex("""[\p{Cc}\u200B\u2060\uFEFF]""")
private val SPACES = Regex("""\s+""")
private const val OPENING_QUOTES = "\"'“„‚‘«»‹›"
private const val CLOSING_QUOTES = "\"'”“‘’»«›‹"
private const val SEPARATORS = ":|-–—·•"
private val SEPARATOR = Regex("""\s+[|\-–—·•:]\s+""")
private val CATEGORY_SEPARATOR = Regex("""\s+:\s+""")

/** The product name in [text], without links, boilerplate and the shop's name; null if none is left. */
private fun nameIn(text: String, site: String?): String? {
    val lines = URL.replace(text, "\n").lines()
        .map { INVISIBLE.replace(it, " ").trim() }
        .map { BOILERPLATE.replace(it, "").trim().trim { c -> c.isWhitespace() || c in SEPARATORS } }
        .filter { line -> line.codePoints().anyMatch(Character::isLetterOrDigit) }
    if (lines.isEmpty()) return null
    var name = SPACES.replace(lines.joinToString(" "), " ")
    if (site != null) name = name.withoutSiteName(site)
    name = name.trim { it.isWhitespace() || it in SEPARATORS }.withoutEnclosingQuotes()
    return name.takeIf { it.codePoints().anyMatch(Character::isLetterOrDigit) }?.shortened()
}

/** `"Blaue Lampe"` → `Blaue Lampe`; quotes inside a name (`Tasse „Kaffee“`, `Monitor 27"`) stay. */
private fun String.withoutEnclosingQuotes(): String {
    if (length < 2 || first() !in OPENING_QUOTES || last() !in CLOSING_QUOTES) return this
    val inner = substring(1, length - 1)
    // `"Sunset" Poster "XL"`: the outer two aren't a pair.
    return if (inner.none { it in OPENING_QUOTES || it in CLOSING_QUOTES }) inner.trim() else this
}

/**
 * Drops the shop's name where page titles put it: "Lego 42115 | eBay", "Nike Air - Weiß - Zalando.de",
 * "Sony WH-1000XM5 : Amazon.de: Elektronik & Foto", "Amazon.com: Sony WH-1000XM5 : Electronics". A name that only
 * starts with it, like "Amazon Basics Kabel", stays.
 */
private fun String.withoutSiteName(site: String): String {
    val shop = """${Regex.escape(site)}(?:\.\p{L}{2,})*"""
    val siteOnly = Regex("""^\s*$shop\s*(?::\s.*)?$""", RegexOption.IGNORE_CASE)
    val leading = Regex("""^\s*$shop\s*:\s+""", RegexOption.IGNORE_CASE)
    var name = leading.replace(this, "")
    // "Shop: Product : Category": after the shop in front, the last " : " part is the shop's category.
    if (name != this) CATEGORY_SEPARATOR.findAll(name).lastOrNull()?.let { name = name.substring(0, it.range.first) }
    while (true) {
        val separator = SEPARATOR.findAll(name).lastOrNull() ?: break
        if (!siteOnly.matches(name.substring(separator.range.last + 1))) break
        name = name.substring(0, separator.range.first)
    }
    while (true) {
        val separator = SEPARATOR.find(name) ?: break
        if (!siteOnly.matches(name.substring(0, separator.range.first))) break
        name = name.substring(separator.range.last + 1)
    }
    return name
}

/** Cut at a word (or else between two characters as people see them, never inside an emoji) and end with "…". */
private fun String.shortened(): String {
    if (length <= MAX_SHARED_NAME_LENGTH) return this
    val space = lastIndexOf(' ', MAX_SHARED_NAME_LENGTH)
    val cut = if (space >= MAX_SHARED_NAME_LENGTH / 2) {
        space
    } else {
        BreakIterator.getCharacterInstance(Locale.ROOT).let {
            it.setText(this)
            it.preceding(MAX_SHARED_NAME_LENGTH)
        }
    }
    return substring(0, cut).trimEnd() + "…"
}
