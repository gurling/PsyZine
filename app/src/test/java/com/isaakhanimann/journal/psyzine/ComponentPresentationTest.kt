// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.delayedTimelineTime
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.projectedCumulativeDoses
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ComponentPresentationTest {
    private val time = Instant.parse("2026-11-03T12:00:00Z")
    private val unit = CustomUnit(1, null, "Blend", Instant.EPOCH, null, null, null,
        false, false, "capsule", "capsules", null, "")
    private val own = CustomRoaInfo(customUnitId = 1, administrationRoute = AdministrationRoute.ORAL,
        commonMinDose = 1.0, strongMinDose = 3.0,
        onsetMin = 1.0, onsetMax = 1.0, onsetUnit = "MINUTES",
        comeupMin = 1.0, comeupMax = 1.0, comeupUnit = "MINUTES",
        peakMin = 10.0, peakMax = 10.0, peakUnit = "MINUTES",
        offsetMin = 1.0, offsetMax = 1.0, offsetUnit = "MINUTES")
    private val duration = own.completeDuration()!!
    private val substance = Substance(name = "A", commonNames = emptyList(), url = "", isApproved = false,
        tolerance = null, crossTolerances = emptyList(), addictionPotential = null, toxicities = emptyList(), categories = emptyList(),
        summary = null, effectsSummary = null, dosageRemark = null, generalRisks = null, longtermRisks = null,
        saferUse = emptyList(), interactions = null,
        roas = listOf(Roa(AdministrationRoute.ORAL, RoaDose("mg", 10.0, 20.0, 30.0, 40.0), duration, null)))
    private val catalog = listOf(substance)
    private fun graph(showChildren: Boolean = false, ownDuration: Boolean = true, hidden: Boolean = false) =
        CustomDefinitionGraph(listOf(unit.copy(areTimelinesOfSubcomponentsShown = showChildren)), listOf(
            DoseComponent(10, 1, "A", "mg", dose = 10.0, isHiddenInTimeline = hidden),
            DoseComponent(11, 1, "A", "mg", dose = 5.0)), if (ownDuration) listOf(own) else emptyList())
    private fun row(id: Int = 1, amount: Double? = 2.0, fullness: StomachFullness? = null) =
        IngestionWithCompanionAndCustomUnit(Ingestion(id, null, time, administrationRoute = AdministrationRoute.ORAL,
            dose = amount, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "capsule",
            experienceId = 42, notes = null, stomachFullness = fullness, consumerName = null, customUnitId = 1), null, unit)
    private fun project(rows: List<IngestionWithCompanionAndCustomUnit>, graph: CustomDefinitionGraph = graph()) =
        TimelineModelFactory.projectRecords(rows, catalog, graph, emptyMap())

    @Test fun hidingIndividualPlainOrNativeRecordsSuppressesCurvesWithoutChangingAmountsOrTotals() {
        val native = row()
        val plain = native.copy(ingestion = native.ingestion.copy(customUnitId = null, substanceName = "A", units = "mg"), customUnit = null)
        for (source in listOf(native, plain)) {
            val visible = project(listOf(source)).single()
            val hidden = project(listOf(source.copy(ingestion = source.ingestion.copy(isHiddenInTimeline = true)))).single()
            assertTrue(visible.curves.isNotEmpty())
            assertTrue(hidden.curves.isEmpty())
            assertEquals(visible.contributions.map { it.subject to it.quantity }, hidden.contributions.map { it.subject to it.quantity })
            val lookup = catalog.associateBy { it.name }
            assertEquals(projectedCumulativeDoses(listOf(visible), graph(), lookup),
                projectedCumulativeDoses(listOf(hidden), graph(), lookup))
            assertEquals(source.ingestion.time, hidden.element.ingestionWithCompanionAndCustomUnit.ingestion.time)
            assertEquals(source.ingestion.dose, hidden.element.ingestionWithCompanionAndCustomUnit.ingestion.dose)
            assertEquals(source.ingestion.id, hidden.element.ingestionWithCompanionAndCustomUnit.ingestion.id)
        }
    }

    @Test fun parentCurveDoesNotReplaceLeafDetailsOrCreateAdditionalStoredRecords() {
        val rows = listOf(row(2), row(1))
        val result = project(rows)
        assertEquals(listOf(1, 2), result.map { it.element.ingestionWithCompanionAndCustomUnit.ingestion.id })
        result.forEach {
            assertEquals(1, it.curves.size)
            assertEquals(SubjectId.Definition(1).key, it.curves.single().groupingKey)
            assertEquals(listOf(20.0, 10.0), it.contributions.map { dose -> dose.quantity.amount })
            assertEquals(2, it.element.components.size)
            assertTrue(it.element.components.none { component -> component.isShownAsCurve })
            assertNull(it.element.calculationError)
        }
        assertEquals(2, rows.size)
        assertEquals(2.0, rows.first().ingestion.dose!!, 0.0)
    }

    @Test fun childCurvesAndHiddenComponentsKeepIndependentAccountingAndIdentities() {
        val graph = graph(showChildren = true, hidden = true)
        val record = project(listOf(row()), graph).single()
        assertEquals(listOf(SubjectId.Definition(1).key, SubjectId.Catalog("A").key), record.curves.map { it.groupingKey })
        assertEquals(listOf(11), record.curves.last().componentPath)
        assertEquals(listOf(false, true), record.element.components.map { it.isShownAsCurve })
        assertEquals(30.0, record.contributions.sumOf { it.quantity.amount!! }, 0.0)
        assertTrue(record.contributions.first().isHiddenInTimeline)
    }

    @Test fun unknownAndInvalidAmountsRemainReadableAndDoNotChangeStoredValues() {
        for (amount in listOf(null, Double.NaN, -1.0, Double.MAX_VALUE)) {
            val raw = row(amount = amount)
            val record = project(listOf(raw)).single()
            if (amount != null) assertNotNull(record.element.calculationError)
            assertEquals(2, record.contributions.size)
            assertTrue(record.contributions.all { it.quantity.amount == null })
            assertTrue(record.element.components.all { it.description == "Unknown dose" })
            assertEquals(amount, raw.ingestion.dose)
        }
    }

    @Test fun cumulativeTotalsCountStoredRecordsRatherThanDuplicateLeavesOrParentCurves() {
        val graph = graph(showChildren = true, hidden = true)
        assertTrue(projectedCumulativeDoses(project(listOf(row()), graph), graph, catalog.associateBy { it.name }).totals.isEmpty())
        val totals = projectedCumulativeDoses(project(listOf(row(), row(2)), graph), graph, catalog.associateBy { it.name })
        assertTrue(totals.notices.isEmpty())
        val total = totals.totals.single()
        assertEquals("pw:A", total.subjectKey)
        assertEquals(60.0, total.cumulativeRouteAndDose.single().cumulativeDose, 0.0)
        assertTrue(total.cumulativeRouteAndDose.single().hasMoreThanOneIngestion)
    }

    @Test fun unknownAndOverflowTotalsGiveReadableNoticesInsteadOfPartialTotals() {
        val graph = graph()
        val unknown = projectedCumulativeDoses(project(listOf(row(), row(2, null)), graph), graph, catalog.associateBy { it.name })
        assertTrue(unknown.totals.isEmpty())
        assertTrue(unknown.notices.single().contains("unknown"))
        val large = CustomDefinitionGraph(listOf(unit), listOf(DoseComponent(10, 1, "A", "mg", dose = Double.MAX_VALUE / 2)), emptyList())
        val overflow = projectedCumulativeDoses(project(listOf(row(1, 2.0), row(2, 2.0)), large), large, catalog.associateBy { it.name })
        assertTrue(overflow.totals.isEmpty())
        assertTrue(overflow.notices.single().contains("original amounts remain saved"))
    }

    @Test fun oralDelaysShiftOnlyPresentationAndRetainIntervalLengthForEveryFullness() {
        for ((fullness, seconds) in StomachFullness.entries.zip(listOf(0L, 2700L, 5400L, 10800L, 14400L))) {
            val raw = row(fullness = fullness)
            raw.ingestion.endTime = time.plusSeconds(30)
            val line = project(listOf(raw)).single().curves.single()
            assertEquals(time, raw.ingestion.time)
            assertEquals(time, line.recordedTime)
            assertEquals(time.plusSeconds(seconds), line.startTime)
            assertEquals(time.plusSeconds(seconds + 30), line.endTime)
            val otherRoute = raw.ingestion.copy(administrationRoute = AdministrationRoute.INHALED)
            assertEquals(time, otherRoute.delayedTimelineTime(time))
        }
        val raw = row(fullness = StomachFullness.FULL)
        raw.ingestion.time = Instant.MAX
        assertNull(raw.ingestion.delayedTimelineTime(Instant.MAX))
        assertNull(project(listOf(raw)).single().curves.single().roaDuration)
    }

    @Test fun cursorUsesCountUnitsAndTypedIdentitiesWithoutAssumingMass() {
        val graph = CustomDefinitionGraph(listOf(unit.copy(name = "A", areTimelinesOfSubcomponentsShown = true)),
            listOf(DoseComponent(10, 1, "A", "mg", dose = 10.0)), listOf(own))
        val lines = project(listOf(row(amount = 1.0)), graph).single().curves
        assertEquals(2, lines.map { it.groupingKey }.distinct().size)
        val atPeak = TimelineCursorModel(lines).at(time.plusSeconds(180))
        assertEquals(1.0, atPeak.first().quantityEquivalent!!, 0.00001)
        assertEquals("1 capsule equivalent", atPeak.first().equivalentText())
        assertNull(atPeak.first().milligramEquivalent)
        assertEquals("10 mg equivalent", atPeak.last().equivalentText())
        val two = project(listOf(row(amount = 2.0)), graph).single().curves
        assertEquals("2 capsules equivalent", TimelineCursorModel(two).at(time.plusSeconds(180)).first().equivalentText())
    }

    @Test fun knownMassUnitsAreConvertedForClassificationButRemainUnchangedInDetails() {
        val graph = CustomDefinitionGraph(listOf(unit), listOf(DoseComponent(10, 1, "A", "g", dose = 0.01)), emptyList())
        val record = project(listOf(row()), graph).single()
        assertEquals(2, record.element.components.single().numDots)
        assertEquals("g", record.contributions.single().units)
        assertEquals(0.02, record.contributions.single().quantity.amount!!, 0.0)
        assertEquals(20.0, record.curves.single().doseInMilligrams!!, 0.0)
    }

    @Test fun projectionAndTotalsPropagateCancellation() {
        assertThrows(CancellationException::class.java) { projectRecordsWithCancellation() }
        val graph = graph()
        assertThrows(CancellationException::class.java) {
            projectedCumulativeDoses(project(listOf(row())), graph, emptyMap()) { throw CancellationException() }
        }
    }

    private fun projectRecordsWithCancellation() = TimelineModelFactory.projectRecords(listOf(row()), catalog, graph(), emptyMap()) {
        throw CancellationException()
    }
}
