// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.ui.tabs.settings.customunits.unitListState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class CustomUnitValidationTest {
    @Test fun requiredLabelsMatchBackupRulesWhilePluralAndUnknownDoseRemainOptional() {
        val errors = customUnitInputErrors("  ", "", "", "", "", false)
        assertEquals(setOf(CustomUnitField.NAME, CustomUnitField.UNIT), errors.keys)
        assertTrue(customUnitInputErrors("Example", "capsule", "", "", "", false).isEmpty())
        assertEquals("", suggestedUnitPlural(""))
        assertEquals("capsules", suggestedUnitPlural("capsule"))
        listOf("g", "mg", "mL", "hits").forEach { assertEquals(it, suggestedUnitPlural(it)) }
    }
    @Test fun zeroSmallScientificAndCommaNumbersAreAccepted() {
        listOf("0", "-0.0", "0,5", "1e-4", Double.MIN_VALUE.toString(), Double.MAX_VALUE.toString()).forEach {
            assertNull(it, numericInputError(it, "Dose"))
        }
        assertNull(numericInputError(" ", "Dose"))
        assertTrue(numericInputError("", "Dose", true)!!.contains("Log unknown dose"))
    }
    @Test fun malformedNegativeNonfiniteAndOversizedValuesHaveActionableErrors() {
        assertEquals("Dose cannot be negative.", numericInputError("-1", "Dose"))
        assertEquals("Uncertainty cannot be negative.", numericInputError("-2", "Uncertainty"))
        listOf("abc", "1,2,3").forEach { assertTrue(numericInputError(it, "Dose")!!.contains("for example")) }
        listOf("NaN", "Infinity", "-Infinity", "1e309").forEach { assertTrue(numericInputError(it, "Dose")!!.contains("not valid")) }
    }
    @Test fun knownDoseRequiresMeasurementAndRetainedUncertaintyIsValidated() {
        val errors = customUnitInputErrors("Example", "cup", "", "0", "NaN", true)
        assertEquals(setOf(CustomUnitField.ORIGINAL_UNIT, CustomUnitField.UNCERTAINTY), errors.keys)
        assertTrue(customUnitInputErrors("Example", "cup", "mg", "1", "ignored", false).isEmpty())
    }
    @Test fun definitionTokenChangesForLabelsCalculationAndArchiveButNotNotes() {
        val unit = CustomUnit.twoCBSample.copy()
        assertEquals(unit.definitionFingerprint(), unit.copy(note = "Only a note").definitionFingerprint())
        listOf(unit.copy(dose = 0.0001), unit.copy(estimatedDoseStandardDeviation = 0.1),
            unit.copy(isEstimate = false), unit.copy(isArchived = true), unit.copy(unit = "other"),
            unit.copy(unitPlural = null), unit.copy(originalUnit = "g"), unit.copy(name = "new label"))
            .forEach { assertNotEquals(unit.definitionFingerprint(), it.definitionFingerprint()) }
    }
    @Test fun tokensPreserveNullsExactBitsAndUnambiguousLabels() {
        val unit = CustomUnit.twoCBSample.copy()
        assertNotEquals(unit.copy(unitPlural = null).definitionFingerprint(), unit.copy(unitPlural = "").definitionFingerprint())
        assertNotEquals(unit.copy(dose = 0.0).definitionFingerprint(), unit.copy(dose = -0.0).definitionFingerprint())
        assertNotEquals(unit.copy(name = "a", unit = "bc").definitionFingerprint(), unit.copy(name = "ab", unit = "c").definitionFingerprint())
    }
    @Test fun operationalErrorsNeverExposeDatabaseOrFileDetails() {
        val privateMessage = "/private/journal.db: SQLite constraint failed"
        assertFalse(customUnitFailure(IllegalStateException(privateMessage), "save this unit").contains(privateMessage))
        assertTrue(customUnitFailure(CustomUnitProblem("Choose another unit."), "save this unit").contains("Choose another unit"))
        assertTrue(customUnitFailure(StaleJournalSession(), "save this unit").contains("reopen"))
        assertTrue(UsedCustomUnit(1).message!!.contains("1 ingestion."))
    }
    @Test fun listFailureIsDifferentFromEmptyAndRetryCanResubscribe() = runBlocking {
        val failed = flow<List<CustomUnit>> { error("private database details") }.unitListState().toList()
        assertTrue(failed.first().loading)
        assertFalse(failed.last().loading)
        assertNotNull(failed.last().error)
        assertFalse(failed.last().error!!.contains("private"))
        val empty = flowOf(emptyList<CustomUnit>()).unitListState().toList().last()
        assertFalse(empty.loading); assertNull(empty.error); assertTrue(empty.units.isEmpty())
    }
    @Test fun cancellationIsNotConvertedIntoAnError() = runBlocking {
        try {
            flow<List<CustomUnit>> { throw CancellationException("cancel") }.unitListState().collect()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
