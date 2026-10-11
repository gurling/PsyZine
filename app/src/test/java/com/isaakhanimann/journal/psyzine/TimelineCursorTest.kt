// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TimelineCursorTest {
    private val start = Instant.parse("2026-01-01T12:00:00Z")
    private fun range(seconds: Float) = DurationRange(seconds, seconds, DurationUnits.SECONDS)
    private val duration = RoaDuration(range(10f), range(20f), range(30f), range(40f), null, null)
    private fun line(dose: Double? = 100.0) = DataForOneEffectLine(
        "Example", AdministrationRoute.ORAL, duration, 1f, .5f, AdaptiveColor.BLUE, start, null, dose)

    @Test fun cursorUsesExactOnsetPeakAndOffsetBoundaries() {
        val model = TimelineCursorModel(listOf(line()))
        listOf(-1L to 0f, 10L to 0f, 20L to .5f, 30L to 1f, 60L to 1f,
            80L to .5f, 100L to 0f, 200L to 0f).forEach { (seconds, fraction) ->
            val readout = model.at(start.plusSeconds(seconds)).single()
            assertEquals(fraction, readout.peakFraction!!, .0001f)
            assertEquals(100 * fraction.toDouble(), readout.milligramEquivalent!!, .0001)
        }
    }

    @Test fun overlappingIngestionsKeepTheirOwnPeakAndDose() {
        val model = TimelineCursorModel(listOf(line(), line(40.0).copy(startTime = start.plusSeconds(40), height = 5f)))
        val values = model.at(start.plusSeconds(80))
        assertEquals(.5f, values[0].peakFraction!!, .0001f)
        assertEquals(50.0, values[0].milligramEquivalent!!, .0001)
        assertEquals(1f, values[1].peakFraction!!, .0001f)
        assertEquals(40.0, values[1].milligramEquivalent!!, .0001)
    }

    @Test fun massUnitsConvertWithoutInventingMassForVolumeOrCounts() {
        assertEquals(125.0, doseToMilligrams(.125, "g")!!, .0001)
        assertEquals(.125, doseToMilligrams(125.0, "µg")!!, .0001)
        assertEquals(.125, doseToMilligrams(125.0, "μg")!!, .0001)
        assertEquals(125.0, doseToMilligrams(125.0, " mg ")!!, .0001)
        assertEquals(12.0, doseToMilligrams(12.0, "mg THC")!!, .0001)
        assertEquals(12.0, doseToMilligrams(12.0, "mg Psilocybin")!!, .0001)
        listOf("ml", "pill", "joint", "mg/ml", null).forEach { assertNull(doseToMilligrams(2.0, it)) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { assertNull(doseToMilligrams(it, "mg")) }
        assertNull(doseToMilligrams(null, "mg"))
        assertNull(doseToMilligrams(Double.MAX_VALUE, "g"))
    }

    @Test fun unknownDoseStillAllowsKnownPeakPercentage() {
        val readout = TimelineCursorModel(listOf(line(null))).at(start.plusSeconds(40)).single()
        assertEquals(1f, readout.peakFraction!!, .0001f)
        assertNull(readout.milligramEquivalent)
    }

    @Test fun partialTimelineDoesNotInventIntensityOnDottedOffset() {
        val partial = duration.copy(offset = null, total = range(100f))
        val model = TimelineCursorModel(listOf(line().copy(roaDuration = partial)))
        assertEquals(.5f, model.at(start.plusSeconds(20)).single().peakFraction!!, .0001f)
        val unknown = model.at(start.plusSeconds(80)).single()
        assertNull(unknown.peakFraction)
        assertNull(unknown.milligramEquivalent)
        assertNull(TimelineCursorModel(listOf(line().copy(roaDuration = null))).at(start).single().peakFraction)
    }

    @Test fun evenlyConsumedRangeUsesItsOwnRenderedPeak() {
        val model = TimelineCursorModel(listOf(line().copy(endTime = start.plusSeconds(20))))
        val values = (0L..140L).map { model.at(start.plusSeconds(it)).single() }
        assertTrue(values.all { it.peakFraction != null && it.peakFraction in 0f..1f })
        assertTrue(values.maxOf { it.peakFraction!! } > .99f)
        assertEquals(0f, values.last().peakFraction!!, .0001f)
        assertTrue(values.all { it.milligramEquivalent != null && it.milligramEquivalent in 0.0..100.0 })
    }

    @Test fun invalidDurationsAndRangesNeverProduceNonFiniteReadouts() {
        val invalid = line().copy(roaDuration = duration.copy(onset = range(Float.NaN)))
        assertNull(TimelineCursorModel(listOf(invalid)).at(start).single().peakFraction)
        val reversed = line().copy(endTime = start.minusSeconds(1))
        assertNull(TimelineCursorModel(listOf(reversed)).at(start).single().milligramEquivalent)
    }
}
