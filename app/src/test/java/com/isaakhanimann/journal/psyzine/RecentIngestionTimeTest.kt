// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.preferences.RecentIngestionTime
import com.isaakhanimann.journal.ui.utils.preservedInstant
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecentIngestionTimeTest {
    @Test fun presetsCaptureAnExactInstantAtSelectionAndKeepItAfterClockAdvances() {
        val selectedAt = Instant.parse("2026-12-31T23:59:30.123456789Z")
        val clock = Clock.fixed(selectedAt, ZoneOffset.UTC)
        val expectedSeconds = listOf(0L, -300L, -900L, -1800L, -3600L, -7200L, 1800L)
        RecentIngestionTime.entries.zip(expectedSeconds).forEach { (preset, seconds) ->
            val selected = preset.at(clock)
            assertEquals(selectedAt.plusSeconds(seconds), selected)
            assertEquals(selected.plusSeconds(600), preset.at(Clock.offset(clock, Duration.ofMinutes(10))))
            assertEquals(selectedAt.plusSeconds(seconds), selected)
        }
    }
    @Test fun elapsedPresetsPreserveBothSidesOfAnAmbiguousLocalTime() {
        val zone = ZoneId.of("Europe/Prague")
        val clock = Clock.fixed(Instant.parse("2026-10-25T01:45:00.123456789Z"), zone)
        val now = RecentIngestionTime.NOW.at(clock)
        val earlier = RecentIngestionTime.ONE_HOUR_AGO.at(clock)
        val local = now.atZone(zone).toLocalDateTime()
        assertEquals(local, earlier.atZone(zone).toLocalDateTime())
        assertNotEquals(now, earlier)
        assertEquals(now, preservedInstant(local, local, now, zone))
        assertEquals(earlier, preservedInstant(local, local, earlier, zone))
    }
    @Test fun futurePresetCrossesTheSpringGapByElapsedTime() {
        val zone = ZoneId.of("Europe/Prague")
        val clock = Clock.fixed(Instant.parse("2026-03-29T00:45:00Z"), zone)
        val selected = RecentIngestionTime.IN_THIRTY_MINUTES.at(clock)
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 15), selected.atZone(zone).toLocalDateTime())
        assertEquals(Duration.ofMinutes(30), Duration.between(clock.instant(), selected))
    }
}
