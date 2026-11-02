// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DefinitionToleranceSourcesTest {
    private fun unit(id: Int) = CustomUnit(id, null, "Definition $id", Instant.EPOCH, null, null, null,
        false, false, "capsule", null, null, "")
    private fun ingestion(definition: Int? = 1, name: String? = null) = Ingestion(id = 5, substanceName = name,
        time = Instant.EPOCH, administrationRoute = AdministrationRoute.ORAL, dose = null,
        isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = null,
        experienceId = 1, notes = null, stomachFullness = null, consumerName = null, customUnitId = definition)
    private fun substance() = Substance(name = "Synthetic", commonNames = emptyList(), url = "", isApproved = false,
        tolerance = Tolerance("Unspecified", "2 days", "1 week"), crossTolerances = emptyList(), addictionPotential = null,
        toxicities = emptyList(), categories = emptyList(), summary = null, effectsSummary = null, dosageRemark = null,
        generalRisks = null, longtermRisks = null, saferUse = emptyList(), interactions = null, roas = emptyList())
    private val catalog get() = mapOf("Synthetic" to substance())

    @Test fun ownPositiveToleranceOverridesChildrenUnlessExplicitlyShown() {
        val root = unit(1).copy(halfToleranceInDays = 2.0, zeroToleranceInDays = 4.0, color = "UNKNOWN_IMPORTED_COLOR")
        val child = unit(2).copy(halfToleranceInDays = 3.0, zeroToleranceInDays = 6.0)
        val parts = listOf(DoseComponent(1, 1, childCustomUnitId = 2), DoseComponent(2, 2, "Synthetic", "mg"))
        fun result(root: CustomUnit, child: CustomUnit) = definitionToleranceEvents(listOf(ingestion()),
            CustomDefinitionGraph(listOf(root, child), parts, emptyList()), catalog, emptyMap())
        val own = result(root, child).events.single()
        assertEquals(SubjectId.Definition(1), own.subject); assertEquals(48.0, own.halfHours!!, 0.0)
        assertEquals("UNKNOWN_IMPORTED_COLOR", own.color)
        val both = result(root.copy(areTolerancesOfSubcomponentsShown = true), child).events
        assertEquals(setOf(SubjectId.Definition(1), SubjectId.Definition(2)), both.map { it.subject }.toSet())
        val recursive = result(root.copy(areTolerancesOfSubcomponentsShown = true), child.copy(areTolerancesOfSubcomponentsShown = true)).events
        assertEquals(setOf(SubjectId.Definition(1), SubjectId.Definition(2), SubjectId.Catalog("Synthetic")), recursive.map { it.subject }.toSet())
    }
    @Test fun unknownDoseAndHiddenComponentsRemainToleranceOccurrences() {
        val root = unit(1).copy(isHiddenInTimeline = true)
        val graph = CustomDefinitionGraph(listOf(root), listOf(DoseComponent(1, 1, "Synthetic", "mg", isHiddenInTimeline = true)), emptyList())
        val event = definitionToleranceEvents(listOf(ingestion().copy(isHiddenInTimeline = true)), graph, catalog, mapOf("Synthetic" to "RED")).events.single()
        assertEquals(48.0, event.halfHours!!, 0.0); assertEquals(168.0, event.zeroHours!!, 0.0)
        assertEquals("RED", event.color); assertEquals(5, event.ingestionId)
    }
    @Test fun missingDefinitionsAreReportedAndPreserveKnownRecordedSubstance() {
        val graph = CustomDefinitionGraph(emptyList(), emptyList(), emptyList())
        val result = definitionToleranceEvents(listOf(ingestion(name = "Synthetic")), graph, catalog, emptyMap())
        assertEquals(setOf(1), result.unresolvedDefinitionIds)
        assertEquals(SubjectId.Catalog("Synthetic"), result.events.single().subject)
        assertTrue(definitionToleranceEvents(listOf(ingestion()), graph, catalog, emptyMap()).events.isEmpty())
    }
    @Test fun cyclesAndOversizedOwnValuesDoNotInventNumericMetadata() {
        val cyclic = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, childCustomUnitId = 1)), emptyList())
        assertEquals(setOf(1), definitionToleranceEvents(listOf(ingestion()), cyclic, catalog, emptyMap()).unresolvedDefinitionIds)
        val huge = CustomDefinitionGraph(listOf(unit(1).copy(halfToleranceInDays = Double.MAX_VALUE)), emptyList(), emptyList())
        val event = definitionToleranceEvents(listOf(ingestion()), huge, catalog, emptyMap()).events.single()
        assertNull(event.halfHours); assertNull(event.zeroHours)
        val unknown = definitionToleranceEvents(listOf(ingestion(null, "Missing")), huge, catalog, emptyMap()).events.single()
        assertNull(unknown.halfHours); assertEquals("Missing", unknown.name)
    }
}
