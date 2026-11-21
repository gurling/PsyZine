// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.experienceToleranceCutoff
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ExperienceToleranceCutoffTest {
    private val date = Instant.parse("2026-11-01T12:00:00Z")
    @Test fun emptyExperienceUsesStoredDate() {
        assertEquals(date, experienceToleranceCutoff(emptyList(), date))
    }
    @Test fun latestStartWinsRegardlessOfOrderOrStoredDate() {
        val starts = listOf(date.plusSeconds(90), date, date.plusSeconds(30))
        assertEquals(date.plusSeconds(90), experienceToleranceCutoff(starts, date.plusSeconds(500)))
        assertEquals(date.plusSeconds(90), experienceToleranceCutoff(starts.reversed(), date.minusSeconds(500)))
    }
    @Test fun futureStartsAndSubsecondPrecisionAreRetained() {
        val future = date.plusSeconds(86400).plusNanos(123456789)
        assertEquals(future, experienceToleranceCutoff(listOf(date, future), date))
    }
}
