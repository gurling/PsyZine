// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.substances.classes.Substance

data class ToleranceSources(val events: List<ToleranceEvent>, val unresolvedDefinitionIds: Set<Int>)

/** Tolerance uses subject occurrences, independently of dose, route and timeline visibility. */
fun definitionToleranceEvents(ingestions: List<Ingestion>, graph: CustomDefinitionGraph,
    catalog: Map<String, Substance>, colors: Map<String, String>, localNames: Set<String> = emptySet()): ToleranceSources {
    data class Source(val subject: SubjectId, val name: String, val color: String?, val half: Double?, val zero: Double?)
    fun named(name: String): Source {
        val tolerance = catalog[name]?.tolerance
        val subject = if (name !in catalog && name in localNames) SubjectId.Local(name) else SubjectId.Catalog(name)
        return Source(subject, name, colors[name], toleranceHours(tolerance?.half), toleranceHours(tolerance?.zero))
    }
    val parts = graph.components.groupBy { it.parentCustomUnitId }
    val memo = mutableMapOf<Int, List<Source>>()
    val unresolved = linkedSetOf<Int>()
    fun daysToHours(value: Double?): Double? = value?.times(24.0)?.takeIf { it.isFinite() && it > 0.0 }
    fun sources(id: Int, path: Set<Int>): List<Source> {
        check(id !in path && path.size < 64) { "Cyclic or deeply nested tolerance definition." }
        memo[id]?.let { return it }
        val unit = graph.definitions[id] ?: error("Missing tolerance definition.")
        val hasOwn = listOf(unit.halfToleranceInDays, unit.zeroToleranceInDays).any { it != null && it.isFinite() && it > 0.0 }
        val result = mutableListOf<Source>()
        if (hasOwn) result += Source(SubjectId.Definition(id), unit.name, unit.color,
            daysToHours(unit.halfToleranceInDays), daysToHours(unit.zeroToleranceInDays))
        if (!hasOwn || unit.areTolerancesOfSubcomponentsShown) {
            val children = parts[id].orEmpty()
            if (children.isEmpty()) {
                if (unit.substanceName != null) result += named(unit.substanceName)
                else if (!hasOwn) result += Source(SubjectId.Definition(id), unit.name, unit.color, null, null)
            } else for (component in children) {
                component.childCustomUnitId?.let { result += sources(it, path + id) }
                    ?: component.substanceName?.let { result += named(it) }
            }
        }
        return result.distinctBy { it.subject }.also { memo[id] = it }
    }
    val events = ingestions.flatMap { ingestion ->
        val id = ingestion.customUnitId
        val candidates = if (id == null) ingestion.substanceName?.let { listOf(named(it)) }.orEmpty()
        else try { sources(id, emptySet()) } catch (_: IllegalStateException) {
            unresolved += id
            // A recorded plain substance name remains meaningful even when its unit was lost.
            ingestion.substanceName?.let { listOf(named(it)) }.orEmpty()
        }
        candidates.map { source -> ToleranceEvent(source.subject, source.name, source.color, ingestion.time, ingestion.id,
            ingestion.consumerName, source.half, source.zero) }
    }
    return ToleranceSources(events, unresolved)
}
