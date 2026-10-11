package io.github.locngu.beforeyoubuy.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.locngu.beforeyoubuy.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The locale the UI is shown in: the per-app language if the user picked one, else the system's.
 * Read it from the configuration (not `Locale.getDefault()`) so formatting always matches the strings.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** [date] in [locale]'s medium style: `10.10.2026`, `10 Oct 2026`, `Oct 10, 2026`. */
fun formatDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

@Composable
@ReadOnlyComposable
fun formattedDate(date: LocalDate): String = formatDate(date, currentLocale())

/** A length of time in calendar days: "61 days" / "61 Tage" (e.g. how long an item has been wanted). */
@Composable
@ReadOnlyComposable
fun durationInDays(days: Int): String = pluralStringResource(R.plurals.duration_days, days, days)

/** How long ago something happened, in calendar days: "Today", "Yesterday", "3 days ago" / "vor 3 Tagen". */
@Composable
@ReadOnlyComposable
fun daysAgo(days: Int): String = when (days) {
    0 -> stringResource(R.string.relative_today)
    1 -> stringResource(R.string.relative_yesterday)
    else -> pluralStringResource(R.plurals.relative_days_ago, days, days)
}
