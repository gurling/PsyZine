// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.experiences.relations.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.stats.*
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import kotlin.random.Random

class NumericSafetyTest {
    private fun ingestion(time: Instant, consumer: String? = null, name: String = "Caffeine", id: Int = 1) =
        IngestionWithCompanionAndCustomUnit(Ingestion(id, name, time, administrationRoute = AdministrationRoute.ORAL,
            dose = 0.0001, isDoseAnEstimate = true, estimatedDoseStandardDeviation = 1.234567, units = "mg", experienceId = 1,
            notes = null, stomachFullness = null, consumerName = consumer, customUnitId = null), null, null)
    @Test fun editorRepresentationsRoundTripAllFiniteDoublesExactly() {
        val cases = listOf(0.0001, 1.234567, Double.MIN_VALUE, Double.MAX_VALUE, -0.0, 1e-200, 1e200) +
            List(10000) { Double.fromBits(Random(it).nextLong()) }.filter { it.isFinite() }
        cases.forEach { value -> assertEquals("Round trip $value", value.toRawBits(), value.toPreservedString().toDouble().toRawBits()) }
    }
    @Test fun noteOnlyEditRetainsDoseAndUncertaintyBits() {
        val original = ingestion(Instant.EPOCH).ingestion
        val edited = original.copy(notes = "only note changed", dose = original.dose!!.toPreservedString().toDouble(),
            estimatedDoseStandardDeviation = original.estimatedDoseStandardDeviation!!.toPreservedString().toDouble())
        assertEquals(original.dose!!.toRawBits(), edited.dose!!.toRawBits())
        assertEquals(original.estimatedDoseStandardDeviation!!.toRawBits(), edited.estimatedDoseStandardDeviation!!.toRawBits())
    }
    @Test fun negativeNonfiniteAndOverflowInputsAreRejectedAndRemainReadable() {
        listOf("-1", "NaN", "Infinity", "-Infinity", "1e309").forEach { text ->
            assertNull(text.nonnegativeDoubleOrNull())
            try { validateDoseText(text, ""); fail(text) } catch (_: IllegalArgumentException) { }
            assertTrue(text.toDouble().toReadableString().isNotEmpty())
        }
        assertEquals(0.0001, "1e-4".nonnegativeDoubleOrNull()!!, 0.0)
        validateDose(null, null); validateDoseText("", ""); validateDoseText("invalid", "invalid", false, false)
        try { validateDoseText("1", "NaN"); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun overflowingCustomUnitProductsAreRejected() {
        val unit = CustomUnit.twoCBSample.copy(dose = Double.MAX_VALUE)
        try { validateCustomUnitDose(2.0, null, false, unit); fail() } catch (_: IllegalArgumentException) { }
    }
}
