// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.*
import kotlinx.coroutines.CancellationException

data class DefinitionCurve(
    val subject: SubjectId,
    val name: String,
    val quantity: DoseQuantity,
    val units: String?,
    val pluralUnits: String?,
    val duration: RoaDuration?,
    val doseReference: RoaDose?,
    val color: AdaptiveColor,
    val componentPath: List<Int>,
)

data class DefinitionTimelineProjection(val curves: List<DefinitionCurve>, val hasInvalidDefinition: Boolean = false)

/** Curve participation is separate from leaf accounting and never changes stored records. */
fun definitionTimelineCurves(
    ingestion: Ingestion,
    graph: CustomDefinitionGraph,
    catalog: Map<String, Substance>,
    companions: Map<String, SubstanceCompanion>,
    checkCancelled: () -> Unit = {},
): DefinitionTimelineProjection {
    val rootId = ingestion.customUnitId ?: return DefinitionTimelineProjection(emptyList())
    val root = graph.definitions[rootId]
    if (ingestion.isHiddenInTimeline || root?.isHiddenInTimeline == true) return DefinitionTimelineProjection(emptyList())
    val children = graph.children
    val ownRoutes = graph.ownRoutes
    val result = mutableListOf<DefinitionCurve>()
    val visiting = mutableSetOf<Int>()
    val colorCache = mutableMapOf<Int, AdaptiveColor?>()

    fun color(id: Int): AdaptiveColor? {
        if (colorCache.containsKey(id)) return colorCache[id]
        val selected = graph.timelineColor(id, companions)
        colorCache[id] = selected
        return selected
    }

    fun leaf(name: String, units: String?, quantity: DoseQuantity, path: List<Int>) {
        checkCancelled()
        val companion = companions[name]
        if (companion?.isHiddenInTimeline == true) return
        val roa = catalog[name]?.getRoa(ingestion.administrationRoute)
        result += DefinitionCurve(SubjectId.Catalog(name), name, quantity, units, null, roa?.roaDuration,
            roa?.roaDose, companion?.color ?: AdaptiveColor.RED, path)
    }

    fun visit(id: Int, quantity: DoseQuantity, path: List<Int>) {
        checkCancelled()
        require(visiting.add(id) && visiting.size <= 64) { "Cyclic or deeply nested definition." }
        try {
            val unit = graph.definitions[id] ?: error("Missing definition.")
            if (unit.isHiddenInTimeline) return
            val parts = children[id].orEmpty()
            val own = ownRoutes[id to ingestion.administrationRoute]
            val duration = own?.completeDuration()
            if (duration != null || parts.isEmpty() && unit.substanceName == null) {
                result += DefinitionCurve(SubjectId.Definition(id), unit.name, quantity, unit.unit, unit.unitPlural,
                    duration, own?.doseReference(unit.unit), color(id) ?: AdaptiveColor.RED, path)
            }
            if (duration == null || unit.areTimelinesOfSubcomponentsShown) {
                if (parts.isEmpty() && duration == null && unit.substanceName != null) {
                    leaf(unit.substanceName, unit.originalUnit,
                        quantity.times(DoseQuantity(unit.dose, unit.isEstimate, unit.estimatedDoseStandardDeviation)), path)
                } else for (part in parts) {
                    checkCancelled()
                    if (part.isHiddenInTimeline) continue
                    require((part.childCustomUnitId != null) xor (part.substanceName != null)) { "Invalid component target." }
                    val childQuantity = quantity.times(DoseQuantity(part.dose, part.isEstimate, part.estimatedDoseStandardDeviation))
                    val nextPath = path + part.id
                    part.childCustomUnitId?.let { visit(it, childQuantity, nextPath) }
                        ?: leaf(requireNotNull(part.substanceName), part.substanceUnit, childQuantity, nextPath)
                    require(result.size <= 100000) { "Too many component curves." }
                }
            }
        } finally { visiting.remove(id) }
    }

    return try {
        // Multiplication validates even a standalone definition's recorded quantity.
        val quantity = DoseQuantity(ingestion.dose, ingestion.isDoseAnEstimate, ingestion.estimatedDoseStandardDeviation)
            .times(DoseQuantity(1.0))
        visit(rootId, quantity, emptyList())
        DefinitionTimelineProjection(result)
    } catch (error: IllegalArgumentException) {
        DefinitionTimelineProjection(listOf(unknownCurve(rootId, root?.name, root?.unit)), true)
    } catch (error: IllegalStateException) {
        if (error is CancellationException) throw error
        DefinitionTimelineProjection(listOf(unknownCurve(rootId, root?.name, root?.unit)), true)
    }
}

private fun unknownCurve(id: Int, name: String?, units: String?) = DefinitionCurve(SubjectId.Definition(id),
    name ?: "Missing definition", DoseQuantity(null), units, null, null, null, AdaptiveColor.RED, emptyList())

fun CustomRoaInfo.doseReference(units: String) = RoaDose(units, lightMinDose, commonMinDose, strongMinDose, heavyMinDose)

fun CustomDefinitionGraph.timelineColor(id: Int, companions: Map<String, SubstanceCompanion>): AdaptiveColor? {
    fun find(current: Int, path: Set<Int>): AdaptiveColor? {
        if (current in path || path.size >= 64) return null
        val unit = definitions[current] ?: return null
        if (unit.color != null) return AdaptiveColor.entries.firstOrNull { it.name == unit.color }
        val first = children[current].orEmpty().firstOrNull()
            ?: return unit.substanceName?.let { companions[it]?.color }
        return first.childCustomUnitId?.let { find(it, path + current) }
            ?: first.substanceName?.let { companions[it]?.color }
    }
    return find(id, emptySet())
}

fun CustomRoaInfo.completeDuration(): RoaDuration? {
    fun range(min: Double?, max: Double?, units: String?): DurationRange? {
        if (min == null || max == null || !min.isFinite() || !max.isFinite() || min < 0 || max < min) return null
        val unit = DurationUnits.entries.firstOrNull { it.name == units } ?: return null
        val result = DurationRange(min.toFloat(), max.toFloat(), unit)
        return result.takeIf { it.minInSec?.isFinite() == true && it.maxInSec?.isFinite() == true }
    }
    return RoaDuration(range(onsetMin, onsetMax, onsetUnit) ?: return null,
        range(comeupMin, comeupMax, comeupUnit) ?: return null,
        range(peakMin, peakMax, peakUnit) ?: return null,
        range(offsetMin, offsetMax, offsetUnit) ?: return null, null, null)
}
