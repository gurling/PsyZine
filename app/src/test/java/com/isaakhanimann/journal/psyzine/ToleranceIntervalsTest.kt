// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ToleranceIntervalsTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val start = LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant()
    private val cutoff = LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant()
    private val subject = SubjectId.Catalog("Synthetic")
    private fun event(time: Instant, id: Int = 1, half: Double? = 48.0, zero: Double? = 96.0, consumer: String? = null) =
        ToleranceEvent(subject, "Synthetic", "BLUE", time, id, consumer, half, zero)
    private fun day(value: String) = LocalDate.parse(value).atStartOfDay(zone).toInstant()

    @Test fun dailyDedupAndDstUseLocalMidnightWithElapsedHourDurations() {
        val anchor = day("2026-03-29")
        val projection = projectTolerance(listOf(event(anchor.plusSeconds(3600)), event(anchor.plusSeconds(7200), 2)), start, cutoff, zone)
        val bands = projection.rows.single().bands
        assertEquals(listOf(ToleranceInterval(anchor, anchor.plus(Duration.ofHours(48)))), bands[ToleranceBand.FULL])
        assertEquals(listOf(ToleranceInterval(anchor.plus(Duration.ofHours(48)), anchor.plus(Duration.ofHours(96)))), bands[ToleranceBand.HALF])
        assertEquals(setOf(1, 2), projection.rows.single().ingestionIds)
        assertEquals(LocalTime.of(1, 0), bands.getValue(ToleranceBand.FULL).single().end.atZone(zone).toLocalTime())
    }
    @Test fun repeatedEventsUnionFullAndSubtractItFromHalfIntervals() {
        val first = day("2026-03-10"); val second = day("2026-03-11")
        val result = projectTolerance(listOf(event(second, 2), event(first)), start, cutoff, zone).rows.single()
        assertEquals(listOf(ToleranceInterval(first, second.plus(Duration.ofHours(48)))), result.bands[ToleranceBand.FULL])
        assertEquals(listOf(ToleranceInterval(second.plus(Duration.ofHours(48)), second.plus(Duration.ofHours(96)))), result.bands[ToleranceBand.HALF])
    }
    @Test fun zeroOnlyAndStrictGreaterThanTwentyFourHourThresholdAreIndependent() {
        val anchor = day("2026-03-10")
        val zeroOnly = projectTolerance(listOf(event(anchor, half = 24.0, zero = 25.5)), start, cutoff, zone).rows.single()
        assertEquals(setOf(ToleranceBand.ZERO_ONLY), zeroOnly.bands.keys)
        assertEquals(anchor.plusSeconds(91800), zeroOnly.bands.getValue(ToleranceBand.ZERO_ONLY).single().end)
        val fractional = projectTolerance(listOf(event(anchor, half = 25.00001, zero = null)), start, cutoff, zone).rows.single()
        assertEquals(anchor.plusSeconds(90000), fractional.bands.getValue(ToleranceBand.FULL).single().end)
        assertTrue(projectTolerance(listOf(event(anchor, half = 24.0, zero = 24.0)), start, cutoff, zone).rows.isEmpty())
        val fullOnly = projectTolerance(listOf(event(anchor, half = 48.0, zero = 36.0)), start, cutoff, zone).rows.single()
        assertEquals(setOf(ToleranceBand.FULL), fullOnly.bands.keys)
    }
    @Test fun cutoffLookbackAndMeFilteringHaveExactBoundaries() {
        val lower = start.minus(Duration.ofDays(90))
        val values = listOf(event(lower, 1, zero = 10000.0), event(lower.plusSeconds(1), 2, zero = 10000.0),
            event(cutoff, 3, consumer = ""), event(cutoff.plusNanos(1), 4), event(cutoff, 5, consumer = "Other"))
        val row = projectTolerance(values, start, cutoff, zone).rows.single()
        assertEquals(setOf(2, 3), row.ingestionIds)
    }
    @Test fun intervalsClipOnlyTheLowerBoundAndKeepForecastBeyondCutoff() {
        val visible = day("2026-03-11")
        val eventTime = day("2026-03-10")
        val row = projectTolerance(listOf(event(eventTime)), visible, visible.plusSeconds(3600), zone).rows.single()
        assertEquals(visible, row.bands.getValue(ToleranceBand.FULL).single().start)
        assertEquals(eventTime.plus(Duration.ofHours(96)), row.bands.getValue(ToleranceBand.HALF).single().end)
        assertTrue(row.bands.getValue(ToleranceBand.HALF).single().end > visible.plusSeconds(3600))
    }
    @Test fun sameLabelsKeepTypedIdentityAndInvalidMetadataNeverCrashes() {
        val value = event(day("2026-03-10"))
        val local = value.copy(subject = SubjectId.Definition(1), ingestionId = 2)
        val rows = projectTolerance(listOf(value, local), start, cutoff, zone).rows
        assertEquals(setOf(subject, SubjectId.Definition(1)), rows.map { it.subject }.toSet())
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, Double.MAX_VALUE)) {
            val result = projectTolerance(listOf(value.copy(halfHours = invalid, zeroHours = invalid)), start, cutoff, zone)
            assertTrue(result.rows.isEmpty()); assertEquals(setOf(subject), result.missingMetadata)
        }
        val expired = projectTolerance(listOf(event(day("2026-03-01"))), day("2026-03-20"), cutoff, zone)
        assertTrue(expired.rows.isEmpty()); assertTrue(expired.missingMetadata.isEmpty())
    }
    @Test fun intervalSubtractionHandlesTouchingAndMultipleDisjointBlocks() {
        val origin = Instant.EPOCH
        fun i(a: Long, b: Long) = ToleranceInterval(origin.plusSeconds(a), origin.plusSeconds(b))
        assertEquals(listOf(i(0, 2), i(4, 6), i(8, 10)), subtractToleranceIntervals(listOf(i(0, 10)), listOf(i(2, 4), i(6, 8))))
        assertEquals(listOf(i(0, 10)), unionToleranceIntervals(listOf(i(4, 10), i(0, 4))))
        assertEquals(emptyList<ToleranceInterval>(), subtractToleranceIntervals(listOf(i(2, 4)), listOf(i(0, 10))))
    }
}
