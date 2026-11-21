// PsyZine modifications, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.IngestionElement
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.getStrengthRelativeToCommonDose
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.room.experiences.delayedTimelineTime
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.CalculatedComponent
import kotlinx.coroutines.CancellationException
import com.isaakhanimann.journal.data.substances.classes.roa.convertDoseUnits

data class PresentedIngestion(val element: IngestionElement, val curves: List<DataForOneEffectLine>,
    val contributions: List<ResolvedDose>)

object TimelineModelFactory {
    /** One record model per stored ingestion; accounting leaves and visible curves stay separate. */
    fun projectRecords(
        rows: List<IngestionWithCompanionAndCustomUnit>,
        substances: List<Substance>,
        graph: CustomDefinitionGraph,
        companions: Map<String, SubstanceCompanion>,
        checkCancelled: () -> Unit = {},
    ): List<PresentedIngestion> {
        val catalog = substances.associateBy { it.name }
        val elements = rows.sortedWith(compareBy<IngestionWithCompanionAndCustomUnit> { it.ingestion.time }
            .thenBy { it.ingestion.id }).map { row ->
            checkCancelled()
            val ingestion = row.ingestion
            val unit = ingestion.customUnitId?.let { graph.definitions[it] } ?: row.customUnit
            val own = ingestion.customUnitId?.let { graph.ownRoutes[it to ingestion.administrationRoute] }
            val roa = catalog[ingestion.substanceName ?: unit?.substanceName]?.getRoa(ingestion.administrationRoute)
            val doseReference = own?.doseReference(unit?.unit ?: ingestion.units.orEmpty()) ?: roa?.roaDose
            val amount = if (own != null) ingestion.dose else row.pureDose
            val units = if (own != null) ingestion.units else row.originalUnit
            val color = ingestion.customUnitId?.let { graph.timelineColor(it, companions) }
                ?: row.substanceCompanion?.color ?: companions[ingestion.substanceName]?.color ?: AdaptiveColor.RED
            val classifiedAmount = doseReference?.let { convertDoseUnits(amount, units, it.units) }
            IngestionElement(row, own?.completeDuration() ?: roa?.roaDuration,
                doseReference?.getNumDots(classifiedAmount), doseReference = doseReference, displayColor = color)
        }
        val linesByRecord = effectLines(elements, substances, graph, companions, checkCancelled).groupBy { it.sourceIngestionId }
        return elements.map { element ->
            checkCancelled()
            val row = element.ingestionWithCompanionAndCustomUnit
            val ingestion = row.ingestion
            val id = ingestion.customUnitId
            var error: String? = null
            val contributions = try {
                val quantity = DoseQuantity(ingestion.dose, ingestion.isDoseAnEstimate, ingestion.estimatedDoseStandardDeviation)
                    .times(DoseQuantity(1.0))
                if (id != null) graph.resolve(id, quantity, checkCancelled)
                else listOf(ResolvedDose(SubjectId.Catalog(checkNotNull(ingestion.substanceName)),
                    ingestion.substanceName, ingestion.units, quantity, emptyList(), ingestion.isHiddenInTimeline))
            } catch (failure: RuntimeException) {
                if (failure is CancellationException) throw failure
                error = "The saved amount or its definition could not be calculated. Edit the ingestion or definition to correct it."
                if (id != null) try { graph.resolve(id, DoseQuantity(null), checkCancelled) }
                catch (unresolved: RuntimeException) {
                    if (unresolved is CancellationException) throw unresolved
                    listOf(ResolvedDose(SubjectId.Definition(id), row.customUnit?.name ?: "Missing definition",
                        ingestion.units, DoseQuantity(null), emptyList(), ingestion.isHiddenInTimeline))
                }
                else ingestion.substanceName?.let { listOf(ResolvedDose(SubjectId.Catalog(it), it, ingestion.units,
                    DoseQuantity(null), emptyList(), ingestion.isHiddenInTimeline)) }.orEmpty()
            }
            val curves = linesByRecord[ingestion.id].orEmpty()
            val visible = curves.map { it.groupingKey to it.componentPath }.toSet()
            val details = if (id != null && graph.children[id].orEmpty().isNotEmpty()) contributions.map { contribution ->
                val reference = when (val subject = contribution.subject) {
                    is SubjectId.Definition -> graph.ownRoutes[subject.id to ingestion.administrationRoute]
                        ?.doseReference(graph.definitions[subject.id]?.unit.orEmpty())
                    is SubjectId.Catalog -> catalog[subject.name]?.getRoa(ingestion.administrationRoute)?.roaDose
                    is SubjectId.Local -> null
                }
                val classifiedAmount = reference?.let { convertDoseUnits(contribution.quantity.amount, contribution.units, it.units) }
                CalculatedComponent(contribution, reference?.getNumDots(classifiedAmount),
                    (contribution.subject.key to contribution.componentPath) in visible,
                    (contribution.subject as? SubjectId.Definition)?.let { graph.definitions[it.id]?.unitPlural })
            } else emptyList()
            PresentedIngestion(element.copy(components = details, calculationError = error), curves, contributions)
        }
    }

    fun effectLines(
        ingestionElements: List<IngestionElement>,
        substances: List<Substance>,
        graph: CustomDefinitionGraph? = null,
        companions: Map<String, SubstanceCompanion> = emptyMap(),
        checkCancelled: () -> Unit = {},
    ): List<DataForOneEffectLine> {
        val catalog = substances.associateBy { it.name }
        return ingestionElements.flatMap { element ->
            checkCancelled()
            val row = element.ingestionWithCompanionAndCustomUnit
            val ingestion = row.ingestion
            if (ingestion.isHiddenInTimeline || row.customUnit?.isHiddenInTimeline == true ||
                ingestion.customUnitId == null && (companions[ingestion.substanceName] ?: row.substanceCompanion)?.isHiddenInTimeline == true) {
                return@flatMap emptyList()
            }
            val lines = if (graph != null && ingestion.customUnitId != null) {
                definitionTimelineCurves(ingestion, graph, catalog, companions, checkCancelled).curves.map { curve ->
                    val reference = curve.doseReference
                    val amount = curve.quantity.amount
                    val classifiedAmount = reference?.let { convertDoseUnits(amount, curve.units, it.units) }
                    val dots = reference?.getNumDots(classifiedAmount)
                    val mass = doseToMilligrams(amount, curve.units)
                    val useOwnUnits = curve.subject is SubjectId.Definition || mass == null
                    val strength = classifiedAmount?.let { reference?.getStrengthRelativeToCommonDose(it) }
                    DataForOneEffectLine(curve.name, ingestion.administrationRoute, curve.duration,
                        strength?.toFloat()?.takeIf { it.isFinite() && it > 0f } ?: 1f,
                        weight(dots), curve.color, ingestion.time, ingestion.endTime, mass,
                        subjectKey = curve.subject.key, sourceIngestionId = ingestion.id,
                        quantity = amount.takeIf { useOwnUnits }, quantityUnit = curve.units.takeIf { useOwnUnits },
                        quantityUnitPlural = curve.pluralUnits.takeIf { useOwnUnits }, componentPath = curve.componentPath)
                }
            } else listOf(ordinaryLine(element, ingestionElements, substances).copy(
                subjectKey = ingestion.substanceName?.let { "pw:$it" }, sourceIngestionId = ingestion.id))
            lines.map { delayedForOral(it, ingestion) }
        }
    }

    private fun weight(dots: Int?) = dots?.coerceIn(0, 4)?.div(4f) ?: 0.5f

    private fun delayedForOral(line: DataForOneEffectLine, ingestion: Ingestion): DataForOneEffectLine {
        val start = ingestion.delayedTimelineTime(line.startTime)
        val end = line.endTime?.let(ingestion::delayedTimelineTime)
        return if (start == null || line.endTime != null && end == null)
            line.copy(roaDuration = null, recordedStartTime = ingestion.time)
        else line.copy(startTime = start, endTime = end, recordedStartTime = ingestion.time)
    }

    private fun ordinaryLine(oneElement: IngestionElement, ingestionElements: List<IngestionElement>, substances: List<Substance>): DataForOneEffectLine {
        val horizontalWeight = if (oneElement.numDots == null) {
            0.5f
        } else if (oneElement.numDots > 4) {
            1f
        } else {
            oneElement.numDots.toFloat() / 4f
        }
        val ingestion = oneElement.ingestionWithCompanionAndCustomUnit.ingestion
        return DataForOneEffectLine(
            substanceName = oneElement.ingestionWithCompanionAndCustomUnit.displayName,
            route = ingestion.administrationRoute,
            roaDuration = oneElement.roaDuration,
            height = getStrengthRelativeToCommonDose(
                ingestion = oneElement.ingestionWithCompanionAndCustomUnit,
                allIngestions = ingestionElements.map { it.ingestionWithCompanionAndCustomUnit },
                roaDose = substances.firstOrNull { it.name == ingestion.substanceName }
                    ?.getRoa(ingestion.administrationRoute)?.roaDose
            ).toFloat().takeIf { it.isFinite() && it > 0f } ?: 1f,
            horizontalWeight = horizontalWeight,
            color = oneElement.ingestionWithCompanionAndCustomUnit.substanceCompanion?.color
                ?: AdaptiveColor.RED,
            startTime = ingestion.time,
            endTime = ingestion.endTime,
            doseInMilligrams = doseToMilligrams(
                oneElement.ingestionWithCompanionAndCustomUnit.pureDose,
                oneElement.ingestionWithCompanionAndCustomUnit.originalUnit
            )
        )
    }
}
