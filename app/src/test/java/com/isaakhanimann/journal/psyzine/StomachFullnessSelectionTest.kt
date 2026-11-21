// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.room.experiences.stomachFullnessSelection
import com.isaakhanimann.journal.data.room.experiences.entities.StomachFullness
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.IngestionSerializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StomachFullnessSelectionTest {
    @Test fun unrecordedChoiceRemainsDistinctFromEmptyAndAllSavedChoicesRecover() {
        assertNull(stomachFullnessSelection(null))
        assertEquals(StomachFullness.EMPTY, stomachFullnessSelection("EMPTY"))
        StomachFullness.entries.forEach { assertEquals(it, stomachFullnessSelection(it.serialized)) }
        assertEquals(listOf("EMPTY", "QUARTERFULL", "HALFFULL", "FULL", "VERYFULL"),
            StomachFullness.entries.map { it.serialized })
    }

    @Test fun unrecognizedRecoveredChoiceIsRejectedWithAnActionableMessage() {
        for (saved in listOf("", "unknown", "FUTURE_VALUE")) {
            try { stomachFullnessSelection(saved); fail("Unrecognized choice was silently accepted") }
            catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Close and reopen")) }
        }
    }

    @Test fun optionalAndRecordedValuesRoundTripWithoutChangingTimesDoseOrUncertainty() {
        val json = Json { encodeDefaults = true }
        val time = Instant.parse("2026-11-11T12:00:00Z")
        for (fullness in listOf(null) + StomachFullness.entries) {
            val original = IngestionSerializable(substanceName = "Example", time = time, endTime = time.plusSeconds(30),
                creationDate = time, administrationRoute = AdministrationRoute.ORAL,
                dose = 0.0001, isDoseAnEstimate = false, estimatedDoseStandardDeviation = 1.234567,
                units = "mg", notes = "note", stomachFullness = fullness)
            val recovered = json.decodeFromString<IngestionSerializable>(json.encodeToString(original))
            assertEquals(original, recovered)
            assertEquals(original.dose!!.toRawBits(), recovered.dose!!.toRawBits())
            assertEquals(original.estimatedDoseStandardDeviation!!.toRawBits(), recovered.estimatedDoseStandardDeviation!!.toRawBits())
        }
    }
}
