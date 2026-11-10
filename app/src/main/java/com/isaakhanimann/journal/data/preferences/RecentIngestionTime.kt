// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.preferences

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class RecentIngestionTime(val label: String, private val minutes: Long) {
    NOW("Now", 0), FIVE_MINUTES_AGO("5 min ago", -5), FIFTEEN_MINUTES_AGO("15 min ago", -15),
    THIRTY_MINUTES_AGO("30 min ago", -30), ONE_HOUR_AGO("1 hr ago", -60),
    TWO_HOURS_AGO("2 hr ago", -120), IN_THIRTY_MINUTES("In 30 min", 30);

    /** Capture at selection; waiting on the form must not move an already chosen time. */
    fun at(clock: Clock): Instant = clock.instant().plus(minutes, ChronoUnit.MINUTES)
}
