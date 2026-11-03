// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DefinitionTimelineProjectionTest {
    private fun unit(id: Int) = CustomUnit(id, null, "Definition $id", Instant.EPOCH, null, null, null,
        false, false, "capsule", "capsules", null, "")
    private fun record(id: Int = 1, amount: Double? = 2.0) = Ingestion(7, null, Instant.EPOCH,
        administrationRoute = AdministrationRoute.ORAL, dose = amount, isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null, units = "capsule", experienceId = 42, notes = null,
        stomachFullness = null, consumerName = null, customUnitId = id)
    private fun own(id: Int) = CustomRoaInfo(customUnitId = id, administrationRoute = AdministrationRoute.ORAL,
        onsetMin = 1.0, onsetMax = 2.0, onsetUnit = "MINUTES",
        comeupMin = 3.0, comeupMax = 4.0, comeupUnit = "MINUTES",
        peakMin = 5.0, peakMax = 6.0, peakUnit = "HOURS",
        offsetMin = 7.0, offsetMax = 8.0, offsetUnit = "MINUTES")
    private val duration = RoaDuration(DurationRange(1f, 2f, DurationUnits.MINUTES), null, null, null, null, null)
    private fun substance(name: String) = Substance(name = name, commonNames = emptyList(), url = "", isApproved = false,
        tolerance = null, crossTolerances = emptyList(), addictionPotential = null, toxicities = emptyList(), categories = emptyList(),
        summary = null, effectsSummary = null, dosageRemark = null, generalRisks = null, longtermRisks = null,
        saferUse = emptyList(), interactions = null,
        roas = listOf(Roa(AdministrationRoute.ORAL, RoaDose("mg", 10.0, 20.0, 30.0, 40.0), duration, null)))
    private val catalog get() = mapOf("A" to substance("A"), "B" to substance("B"))

    @Test fun ownCompleteDurationTakesPrecedenceAndChildCurvesAreIndependent() {
        val component = DoseComponent(1, 1, "A", "mg", dose = 10.0)
        val hiddenChildren = CustomDefinitionGraph(listOf(unit(1)), listOf(component), listOf(own(1)))
        val parent = definitionTimelineCurves(record(), hiddenChildren, catalog, emptyMap()).curves.single()
        assertEquals(SubjectId.Definition(1), parent.subject)
        assertEquals(2.0, parent.quantity.amount!!, 0.0)
        assertEquals("capsule", parent.units)
        assertEquals(300f * 60f, parent.duration!!.peak!!.minInSec!!, 0f)
        val shownChildren = CustomDefinitionGraph(listOf(unit(1).copy(areTimelinesOfSubcomponentsShown = true)),
            listOf(component), listOf(own(1)))
        val lines = definitionTimelineCurves(record(), shownChildren, catalog, emptyMap()).curves
        assertEquals(listOf(SubjectId.Definition(1), SubjectId.Catalog("A")), lines.map { it.subject })
        assertEquals(20.0, lines[1].quantity.amount!!, 0.0)
    }

    @Test fun thresholdsAndIncompleteOwnDurationStillUseComponentCurves() {
        val partial = own(1).copy(offsetMax = null, commonMinDose = 1.0, strongMinDose = 2.0)
        val graph = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, "A", "mg", dose = 10.0)), listOf(partial))
        val line = definitionTimelineCurves(record(), graph, catalog, emptyMap()).curves.single()
        assertEquals(SubjectId.Catalog("A"), line.subject)
        assertEquals(duration, line.duration)
        assertEquals("mg", line.doseReference!!.units)
    }

    @Test fun nestedOwnDurationAndDuplicateLeafContributionsKeepPathsAndQuantities() {
        val parts = listOf(DoseComponent(10, 1, childCustomUnitId = 2, dose = 3.0),
            DoseComponent(11, 2, "A", "mg", dose = 0.0001), DoseComponent(12, 2, "A", "mg", dose = 0.0002))
        val graph = CustomDefinitionGraph(listOf(unit(1), unit(2).copy(areTimelinesOfSubcomponentsShown = true)), parts, listOf(own(2)))
        val curves = definitionTimelineCurves(record(), graph, catalog, emptyMap()).curves
        assertEquals(3, curves.size)
        assertEquals(SubjectId.Definition(2), curves[0].subject)
        assertEquals(6.0, curves[0].quantity.amount!!, 0.0)
        assertEquals(listOf(10, 11), curves[1].componentPath)
        assertEquals(listOf(10, 12), curves[2].componentPath)
        assertEquals(6 * 0.0001, curves[1].quantity.amount!!, 0.0)
        assertEquals(6 * 0.0002, curves[2].quantity.amount!!, 0.0)
        assertEquals(7, record().id)
    }

    @Test fun visibilityRemovesCurvesWithoutRemovingAccountingLeaves() {
        val graph = CustomDefinitionGraph(listOf(unit(1)), listOf(
            DoseComponent(1, 1, "A", "mg", dose = 10.0, isHiddenInTimeline = true),
            DoseComponent(2, 1, "B", "mg", dose = 20.0)), emptyList())
        assertEquals(listOf("B"), definitionTimelineCurves(record(), graph, catalog, emptyMap()).curves.map { it.name })
        assertEquals(listOf("A", "B"), graph.resolve(1, DoseQuantity(2.0)).map { it.name })
        assertTrue(definitionTimelineCurves(record().copy(isHiddenInTimeline = true), graph, catalog, emptyMap()).curves.isEmpty())
        val hidden = CustomDefinitionGraph(listOf(unit(1).copy(isHiddenInTimeline = true)), graph.components, emptyList())
        assertTrue(definitionTimelineCurves(record(), hidden, catalog, emptyMap()).curves.isEmpty())
        val companion = SubstanceCompanion("B", AdaptiveColor.BLUE, isHiddenInTimeline = true)
        assertTrue(definitionTimelineCurves(record(), graph, catalog, mapOf("B" to companion)).curves.isEmpty())
        assertEquals(2, graph.resolve(1, DoseQuantity(2.0)).size)
    }

    @Test fun parentAndCatalogWithEqualNamesRetainDistinctIdentities() {
        val graph = CustomDefinitionGraph(listOf(unit(1).copy(name = "A", areTimelinesOfSubcomponentsShown = true)),
            listOf(DoseComponent(1, 1, "A", "mg", dose = 10.0)), listOf(own(1)))
        val curves = definitionTimelineCurves(record(), graph, catalog, emptyMap()).curves
        assertEquals(listOf("A", "A"), curves.map { it.name })
        assertEquals(2, curves.map { it.subject.key }.distinct().size)
    }

    @Test fun colorUsesEarliestComponentAndDoesNotSkipMissingMetadataForLaterColors() {
        val graph = CustomDefinitionGraph(listOf(unit(1)), listOf(
            DoseComponent(1, 1, "B", "mg", dose = 1.0, creationDate = Instant.ofEpochSecond(2)),
            DoseComponent(2, 1, "A", "mg", dose = 1.0, creationDate = Instant.EPOCH)), listOf(own(1)))
        val colors = mapOf("A" to SubstanceCompanion("A", AdaptiveColor.BLUE), "B" to SubstanceCompanion("B", AdaptiveColor.GREEN))
        assertEquals(AdaptiveColor.BLUE, definitionTimelineCurves(record(), graph, catalog, colors).curves.single().color)
        assertEquals(AdaptiveColor.RED, definitionTimelineCurves(record(), graph, catalog, colors - "A").curves.single().color)
    }

    @Test fun unknownQuantitiesAndMissingCatalogRemainReadableWithoutInventingDurations() {
        val graph = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, "Missing", "drop", dose = 2.0)), emptyList())
        val line = definitionTimelineCurves(record(amount = null), graph, emptyMap(), emptyMap()).curves.single()
        assertEquals("Missing", line.name)
        assertEquals("drop", line.units)
        assertNull(line.quantity.amount)
        assertNull(line.duration)
        assertNull(line.doseReference)
        val empty = CustomDefinitionGraph(listOf(unit(1)), emptyList(), emptyList())
        assertEquals(SubjectId.Definition(1), definitionTimelineCurves(record(), empty, emptyMap(), emptyMap()).curves.single().subject)
    }

    @Test fun legacyUnitsWithoutBackfilledComponentsRetainTheirEffectiveDose() {
        val legacy = unit(1).copy(substanceName = "A", dose = 10.0, originalUnit = "mg", administrationRoute = AdministrationRoute.ORAL)
        val graph = CustomDefinitionGraph(listOf(legacy), emptyList(), emptyList())
        val line = definitionTimelineCurves(record(), graph, catalog, emptyMap()).curves.single()
        assertEquals(SubjectId.Catalog("A"), line.subject)
        assertEquals(20.0, line.quantity.amount!!, 0.0)
        assertEquals("mg", line.units)
    }

    @Test fun invalidStoredGraphsAndOverflowProduceUnknownContentWithoutMutatingTheRecord() {
        val missing = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, childCustomUnitId = 2, dose = 1.0)), emptyList())
        val cycle = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, childCustomUnitId = 1, dose = 1.0)), emptyList())
        val overflow = CustomDefinitionGraph(listOf(unit(1)), listOf(DoseComponent(1, 1, "A", "mg", dose = Double.MAX_VALUE)), emptyList())
        for (graph in listOf(missing, cycle, overflow)) {
            val ingestion = record()
            val result = definitionTimelineCurves(ingestion, graph, catalog, emptyMap())
            assertTrue(result.hasInvalidDefinition)
            assertNull(result.curves.single().quantity.amount)
            assertNull(result.curves.single().duration)
            assertEquals(2.0, ingestion.dose!!, 0.0)
        }
        assertTrue(definitionTimelineCurves(record(amount = Double.NaN), missing, catalog, emptyMap()).hasInvalidDefinition)
    }

    @Test fun invalidDurationsRemainUnknownAndCancellationIsNotAnErrorResult() {
        assertNull(own(1).copy(peakMin = Double.MAX_VALUE, peakMax = Double.MAX_VALUE).completeDuration())
        assertNull(own(1).copy(onsetUnit = "unsupported").completeDuration())
        val graph = CustomDefinitionGraph(listOf(unit(1)), emptyList(), listOf(own(1)))
        assertThrows(CancellationException::class.java) {
            definitionTimelineCurves(record(), graph, catalog, emptyMap()) { throw CancellationException() }
        }
    }
}
