// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StatisticsIntervalsTest {
    @Test fun currentBreakUsesLongestRecordedEndAndIgnoresFutureStarts() {
        val rows = listOf(row(1, 1, 0, 100), row(2, 2, 10, 20), row(3, 3, 300))
        val result = statisticsCurrentBreak(rows, Instant.ofEpochSecond(150))!!
        assertEquals(1, result.ingestionId)
        assertEquals(Duration.ofSeconds(50), result.elapsed)
        assertFalse(result.ongoing)
        assertTrue(statisticsCurrentBreak(rows, Instant.ofEpochSecond(50))!!.ongoing)
        assertNull(statisticsCurrentBreak(listOf(row(1, 1, 300)), Instant.ofEpochSecond(100)))
        assertNull(statisticsCurrentBreak(emptyList(), Instant.EPOCH))
    }
    private fun row(id: Int, experience: Int, start: Long, end: Long? = null) =
        StatisticsIntervalRecord(id, experience, Instant.ofEpochSecond(start), end?.let(Instant::ofEpochSecond))

    @Test fun sortsDeduplicatesAndUsesStartsWithinAnExperience() {
        val result = statisticsIntervals(listOf(row(2, 1, 20), row(1, 1, 10, 100), row(2, 1, 20)))
        assertEquals(Duration.ofSeconds(10), result.withinExperiences.single().elapsed)
        assertTrue(result.betweenExperiences.isEmpty())
        assertEquals(1, result.withinExperiences.single().fromIngestionId)
    }
    @Test fun breaksUseRecordedEndsAndRetainTheLongestOverlappingSpan() {
        val result = statisticsIntervals(listOf(row(1, 1, 0, 100), row(2, 2, 10, 20), row(3, 3, 30), row(4, 4, 120)))
        assertEquals(listOf(Duration.ZERO, Duration.ZERO, Duration.ofSeconds(20)), result.betweenExperiences.map { it.elapsed })
        assertEquals(1, result.betweenExperiences.last().fromIngestionId)
    }
    @Test fun emptySingleTiedAndReversedEndRecordsAreSafe() {
        assertEquals(StatisticsIntervals(), statisticsIntervals(emptyList()))
        assertEquals(StatisticsIntervals(), statisticsIntervals(listOf(row(1, 1, 10))))
        val result = statisticsIntervals(listOf(row(2, 2, 10), row(1, 1, 10, 0), row(3, 3, 11)))
        assertEquals(listOf(Duration.ZERO, Duration.ofSeconds(1)), result.betweenExperiences.map { it.elapsed })
    }
    @Test fun durationsAreElapsedAcrossDstAndCancellationPropagates() {
        val start = Instant.parse("2026-03-28T23:00:00Z")
        val end = Instant.parse("2026-03-29T22:00:00Z")
        val result = statisticsIntervals(listOf(StatisticsIntervalRecord(1, 1, start, null), StatisticsIntervalRecord(2, 2, end, null)))
        assertEquals(Duration.ofHours(23), result.betweenExperiences.single().elapsed)
        assertThrows(CancellationException::class.java) { statisticsIntervals(listOf(row(1, 1, 0))) { throw CancellationException() } }
    }
}
