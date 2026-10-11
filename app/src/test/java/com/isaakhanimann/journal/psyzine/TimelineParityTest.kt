// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toEstimatedTotalTimeline
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TimelineParityTest {
    private val start = Instant.parse("2026-01-01T12:00:00Z")
    private fun range(seconds: Float) = DurationRange(seconds, seconds, DurationUnits.SECONDS)
    private fun phases(onset: DurationRange? = null, comeup: DurationRange? = null, peak: DurationRange? = null,
        offset: DurationRange? = null, total: DurationRange? = null, afterglow: DurationRange? = null) =
        RoaDuration(onset, comeup, peak, offset, total, afterglow)

    @Test fun phasesHaveExactBoundariesIncludingAfterglow() {
        val data = phases(range(10f), range(20f), range(30f), range(40f), afterglow = range(50f))
        listOf(0L to IngestionPhase.ONSET, 9L to IngestionPhase.ONSET, 10L to IngestionPhase.COMEUP,
            30L to IngestionPhase.PEAK, 60L to IngestionPhase.OFFSET, 100L to IngestionPhase.AFTERGLOW,
            150L to IngestionPhase.ENDED).forEach { (seconds, phase) ->
            assertEquals(phase, calculateCurrentPhase(start, data, start.plusSeconds(seconds)))
        }
    }
    @Test fun missingPhasesAreSkippedAndTotalDoesNotInventPhases() {
        assertEquals(IngestionPhase.PEAK, calculateCurrentPhase(start, phases(peak = range(60f)), start))
        assertEquals(IngestionPhase.UNKNOWN, calculateCurrentPhase(start, null, start))
        assertEquals(IngestionPhase.UNKNOWN, calculateCurrentPhase(start, phases(total = range(60f)), start))
    }
    @Test fun midpointUnitsAndFutureTimesAreDeterministic() {
        val data = phases(onset = DurationRange(1f, 3f, DurationUnits.MINUTES))
        assertEquals(IngestionPhase.ONSET, calculateCurrentPhase(start, data, start.minusSeconds(1)))
        assertEquals(IngestionPhase.ONSET, calculateCurrentPhase(start, data, start.plusSeconds(119)))
        assertEquals(IngestionPhase.ENDED, calculateCurrentPhase(start, data, start.plusSeconds(120)))
    }
    @Test fun invalidPhasesDoNotCreateNonFiniteRanges() {
        assertEquals(IngestionPhase.UNKNOWN, calculateCurrentPhase(start, phases(onset = range(Float.NaN), peak = range(-5f)), start))
        assertNull(phases(onset = range(Float.POSITIVE_INFINITY)).toEstimatedTotalTimeline(.5f, 0f, 1f))
        assertNull(phases(onset = range(-1f)).toEstimatedTotalTimeline(.5f, 0f, 1f))
        assertNull(phases(onset = range(0f)).toEstimatedTotalTimeline(.5f, 0f, 1f))
        assertNull(phases().toEstimatedTotalTimeline(.5f, 0f, 1f))
        assertEquals(IngestionPhase.UNKNOWN, calculateCurrentPhase(start, phases(onset = DurationRange(-1f, 10f, DurationUnits.SECONDS)), start))
        assertNull(phases(onset = DurationRange(10f, 1f, DurationUnits.SECONDS), peak = range(100f)).toEstimatedTotalTimeline(.5f, 0f, 1f))
    }
    @Test fun partialDurationsProduceEstimatedTotals() {
        val data = phases(comeup = DurationRange(1f, 2f, DurationUnits.MINUTES), peak = DurationRange(1f, 2f, DurationUnits.HOURS), afterglow = range(30f))
        val timeline = data.toEstimatedTotalTimeline(.25f, 12f, 2f)!!
        assertEquals(3690f, timeline.total.minInSeconds, .01f)
        assertEquals(7350f, timeline.total.maxInSeconds, .01f)
        assertEquals(7362f, timeline.endOfLineRelativeToStartInSeconds, .01f)
    }
}
