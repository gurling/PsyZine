package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.ui.tabs.settings.definitions.*
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DefinitionDraftTest {
    @Test fun missingRequiredFieldsAndBadNumbersHaveFieldSpecificMessages() {
        val draft = DefinitionDraft(components = listOf(ComponentDraft("a", substance = "Synthetic", dose = "NaN", uncertainty = "-2", createdAt = 0)))
        val errors = draft.errors()
        assertTrue(errors.getValue("name").contains("name"))
        assertTrue(errors.getValue("unit").contains("singular"))
        assertTrue(errors.getValue("a.dose").contains("NaN"))
        assertTrue(errors.getValue("a.uncertainty").contains("negative"))
        assertTrue(errors.getValue("a.units").contains("measurement"))
    }
    @Test fun blankAmountIsUnknownButInvalidAmountCannotBecomeUnknown() {
        val valid = DefinitionDraft("Synthetic", "capsule", components = listOf(ComponentDraft("a", substance = "Synthetic", units = "mg", createdAt = 0)))
        assertNull(valid.content(null, Instant.EPOCH).components.single().dose)
        assertFalse(valid.copy(components = valid.components.map { it.copy(dose = "oops") }).errors().isEmpty())
    }
    @Test fun aNoteOnlyEditPreservesTinyAmountsAndUncertaintyExactly() {
        val unit = CustomUnit(1, null, "Synthetic", Instant.EPOCH, null, null, null, false, false, "capsule", null, null, "")
        val part = DoseComponent(7, 1, "Synthetic", "mg", dose = 1.234567, isEstimate = true, estimatedDoseStandardDeviation = 0.0000001)
        val original = DefinitionWithContent(unit, listOf(part), emptyList())
        val saved = original.toDraft().copy(note = "changed").content(unit, Instant.EPOCH)
        assertEquals(part.dose!!, saved.components.single().dose!!, 0.0)
        assertEquals(part.estimatedDoseStandardDeviation!!, saved.components.single().estimatedDoseStandardDeviation!!, 0.0)
    }
    @Test fun partialAndReversedDurationRangesHaveReadableErrors() {
        val base = DefinitionDraft("Synthetic", "capsule", roas = listOf(RoaDraft("r", route = AdministrationRoute.ORAL,
            fields = mapOf("onsetMin" to "4", "onsetMax" to "2", "onsetUnit" to "MINUTES"), createdAt = 0)))
        assertTrue(base.errors().getValue("r.range").contains("minimum"))
        assertTrue(base.copy(roas = base.roas.map { it.copy(fields = mapOf("onsetMin" to "4")) }).errors().getValue("r.range").contains("time unit"))
    }
    @Test fun primaryReplacementMatchesReferenceOrderAndKeepsValidSelection() {
        val tags = listOf(SubstanceTag("b", "zebra", "RED"), SubstanceTag("a", "Alpha", "BLUE"))
        assertEquals("a", primaryTagId(tags, setOf("a", "b"), null))
        assertEquals("b", primaryTagId(tags, setOf("a", "b"), "b"))
        assertNull(primaryTagId(tags, emptySet(), "b"))
    }
}
