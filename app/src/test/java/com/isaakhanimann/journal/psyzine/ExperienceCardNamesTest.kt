// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ExperienceCardNamesTest {
    private fun unit(id: Int, name: String) = CustomUnit(id, null, name, Instant.EPOCH, null, null, null,
        false, false, "capsule", null, null, "")
    private fun ingestion(id: Int, definition: Int? = null, name: String? = null) = Ingestion(id, name, Instant.ofEpochSecond(id.toLong()),
        administrationRoute = AdministrationRoute.ORAL, dose = Double.NaN, isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null, units = null, experienceId = 42, notes = null,
        stomachFullness = null, consumerName = null, customUnitId = definition)

    @Test fun directIngredientsRespectCreationOrderAndDeduplicateAcrossStoredRecords() {
        val units = mapOf(1 to unit(1, "Blend"))
        val parts = mapOf(1 to listOf(DoseComponent(1, 1, "B", "mg", dose = null, creationDate = Instant.ofEpochSecond(5)),
            DoseComponent(2, 1, "A", "mg", dose = Double.MAX_VALUE, creationDate = Instant.EPOCH),
            DoseComponent(3, 1, "A", "mg", dose = 1.0)))
        val names = experienceCardNames(listOf(ingestion(3, 1), ingestion(1, name = "First"), ingestion(2, 1)), units, parts)
        assertEquals(listOf("First", "Blend"), names.selected(false))
        assertEquals(listOf("First", "A", "B"), names.selected(true))
    }

    @Test fun nestedOrEmptyDefinitionsKeepParentNamesWithoutFlattening() {
        val units = mapOf(1 to unit(1, "Parent"), 2 to unit(2, "Child"), 3 to unit(3, "Empty"))
        val parts = mapOf(1 to listOf(DoseComponent(1, 1, "A", "mg", dose = 1.0), DoseComponent(2, 1, childCustomUnitId = 2)),
            2 to listOf(DoseComponent(3, 2, "B", "mg", dose = 1.0)))
        val names = experienceCardNames(listOf(ingestion(1, 1), ingestion(2, 3)), units, parts)
        assertEquals(listOf("Parent", "Empty"), names.components)
    }

    @Test fun visibilityAndInvalidDoseMathDoNotHideSummaryNamesOrChangeStoredRecords() {
        val raw = ingestion(1, 1).copy(isHiddenInTimeline = true)
        val units = mapOf(1 to unit(1, "Hidden blend").copy(isHiddenInTimeline = true))
        val parts = mapOf(1 to listOf(DoseComponent(1, 1, "Hidden ingredient", "mg", dose = Double.MAX_VALUE, isHiddenInTimeline = true)))
        assertEquals(listOf("Hidden ingredient"), experienceCardNames(listOf(raw), units, parts).components)
        assertTrue(raw.dose!!.isNaN())
    }

    @Test fun missingMetadataHasAReadableFallbackAndCancellationPropagates() {
        assertEquals(listOf("Known", "Unknown substance"), experienceCardNames(
            listOf(ingestion(1, name = "Known"), ingestion(2, 99)), emptyMap(), emptyMap()).parents)
        assertTrue(experienceCardNames(emptyList(), emptyMap(), emptyMap()).components.isEmpty())
        assertThrows(CancellationException::class.java) {
            experienceCardNames(listOf(ingestion(1)), emptyMap(), emptyMap()) { throw CancellationException() }
        }
    }
}
