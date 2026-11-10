// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.ui.tabs.journal.components.relativeJournalDateText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RelativeJournalDateTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")

    @Test fun minuteHourAndDayBoundariesHaveCorrectDirectionAndSingularLabels() {
        for ((seconds, amount) in listOf(60L to "1 minute", 120L to "2 minutes", 3600L to "1 hour",
            5400L to "1.5 hours", 86_400L to "1 day", 172_800L to "2 days")) {
            assertEquals("$amount ago", relativeJournalDateText(now.minusSeconds(seconds), now))
            assertEquals("in $amount", relativeJournalDateText(now.plusSeconds(seconds), now))
        }
    }
    @Test fun nearFutureEntriesAreNotDescribedAsPastOrJustNow() {
        assertEquals("just now", relativeJournalDateText(now, now))
        assertEquals("just now", relativeJournalDateText(now.minusSeconds(59), now))
        assertEquals("in less than a minute", relativeJournalDateText(now.plusSeconds(59), now))
        assertEquals("in less than a minute", relativeJournalDateText(now.plusNanos(1), now))
    }
    @Test fun elapsedLabelsStayAccurateAcrossMidnightAndDaylightSavingChanges() {
        val zone = ZoneId.of("Europe/Prague")
        val midnight = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant()
        assertEquals("1 minute ago", relativeJournalDateText(midnight.minusSeconds(30), midnight.plusSeconds(30)))
        val spring = LocalDate.of(2026, 3, 29).atStartOfDay(zone).toInstant()
        val next = LocalDate.of(2026, 3, 30).atStartOfDay(zone).toInstant()
        assertEquals("23 hours ago", relativeJournalDateText(spring, next))
        val first = java.time.LocalDateTime.of(2026, 10, 25, 2, 30).atZone(zone).withEarlierOffsetAtOverlap().toInstant()
        val second = java.time.LocalDateTime.of(2026, 10, 25, 2, 30).atZone(zone).withLaterOffsetAtOverlap().toInstant()
        assertEquals("1 hour ago", relativeJournalDateText(first, second))
    }
    @Test fun oldAndFutureDatesRetainTheEstablishedApproximateLongRangeUnits() {
        assertEquals("3 weeks ago", relativeJournalDateText(now.minusSeconds(21 * 86_400), now))
        assertEquals("in 3 months", relativeJournalDateText(now.plusSeconds(90 * 86_400), now))
        assertEquals("2 years ago", relativeJournalDateText(now.minusSeconds(730 * 86_400), now))
    }
}
