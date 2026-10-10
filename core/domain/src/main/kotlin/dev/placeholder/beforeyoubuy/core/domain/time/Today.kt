package dev.placeholder.beforeyoubuy.core.domain.time

import java.time.Clock
import java.time.LocalDate

/** Today's calendar date in the clock's time zone (ADR-0018: time comes from an injected [Clock]). */
fun Clock.today(): LocalDate = LocalDate.now(this)
