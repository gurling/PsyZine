package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DefinitionGraphTest {
    private fun unit(id: Int) = CustomUnit(id, null, "Definition $id", Instant.EPOCH, null, null, null,
        false, false, "capsule", null, null, "")
    @Test fun nestedDefinitionsMultiplyWithoutLosingIdentityOrPrecision() {
        val graph = CustomDefinitionGraph(listOf(unit(1), unit(2)), listOf(
            DoseComponent(1, 1, childCustomUnitId = 2, dose = 3.0),
            DoseComponent(2, 2, "Synthetic A", "mg", dose = 0.0001)), emptyList())
        graph.validate()
        val result = graph.resolve(1, DoseQuantity(2.0)).single()
        assertEquals("Synthetic A", result.name)
        assertEquals(0.0001 * 3 * 2, result.quantity.amount!!, 0.0)
        assertEquals(listOf(1, 2), result.componentPath)
    }
    @Test fun uncertaintyUsesStableProductVarianceAndPreservesTinyPositiveValues() {
        val result = DoseQuantity(2.0, true, 0.2).times(DoseQuantity(3.0, true, 0.3))
        assertEquals(6.0, result.amount!!, 0.0)
        assertEquals(kotlin.math.sqrt(0.36 + 0.36 + 0.0036), result.uncertainty!!, 1e-14)
        assertTrue(DoseQuantity(1.0, true, 1e-12).times(DoseQuantity(1.0)).uncertainty!! > 0)
    }
    @Test fun uncertaintyOnUnestimatedValuesDoesNotBecomeAnEstimate() {
        val result = DoseQuantity(2.0, false, 9.0).times(DoseQuantity(3.0, false, 8.0))
        assertFalse(result.isEstimate); assertNull(result.uncertainty)
    }
    @Test fun unknownQuantitiesRemainUnknownAndHiddenLeavesRemainCountable() {
        val graph = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, "Synthetic", "mg",
            dose = 2.0, isHiddenInTimeline = true)), emptyList())
        val result = graph.resolve(1, DoseQuantity(null)).single()
        assertNull(result.quantity.amount); assertTrue(result.isHiddenInTimeline)
    }
    @Test fun cyclesAndMissingChildrenAreRejectedBeforeWriting() {
        val units = listOf(unit(1), unit(2))
        val cycle = CustomDefinitionGraph(units, listOf(DoseComponent(1, 1, childCustomUnitId = 2), DoseComponent(2, 2, childCustomUnitId = 1)), emptyList())
        assertThrows(IllegalArgumentException::class.java) { cycle.validate() }
        val missing = CustomDefinitionGraph(units, listOf(DoseComponent(1, 1, childCustomUnitId = 3)), emptyList())
        assertThrows(IllegalArgumentException::class.java) { missing.validate() }
    }
    @Test fun routeOverridesPreserveLegacyRouteAndPreferOwnRoutes() {
        val legacy = unit(1).copy(substanceName = "Synthetic", administrationRoute = AdministrationRoute.ORAL, originalUnit = "mg")
        val graph = CustomDefinitionGraph(listOf(legacy), emptyList(), emptyList())
        assertEquals(listOf(AdministrationRoute.ORAL), graph.availableRoutes(1) { listOf(AdministrationRoute.INHALED) })
        val own = CustomDefinitionGraph(listOf(legacy), emptyList(), listOf(CustomRoaInfo(customUnitId = 1, administrationRoute = AdministrationRoute.INHALED)))
        assertEquals(listOf(AdministrationRoute.INHALED), own.availableRoutes(1) { emptyList() })
    }
    @Test fun overflowAndNonfiniteAmountsFailClearly() {
        assertThrows(IllegalArgumentException::class.java) { DoseQuantity(Double.MAX_VALUE).times(DoseQuantity(2.0)) }
        assertThrows(IllegalArgumentException::class.java) { DoseQuantity(Double.NaN).times(DoseQuantity(1.0)) }
        assertThrows(IllegalArgumentException::class.java) { DoseQuantity(-1.0).times(DoseQuantity(1.0)) }
    }
    @Test fun tagNormalizationIsAccentCaseAndWhitespaceInsensitive() {
        assertEquals(normalizedTagName("Café"), normalizedTagName("  CAFE  "))
        assertEquals(SubjectId.Definition(7), SubjectId.parse("custom:7"))
        assertThrows(IllegalArgumentException::class.java) { SubjectId.parse("custom:-2") }
    }
}
