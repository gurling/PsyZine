// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.validateDose
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlin.math.hypot

data class DoseQuantity(val amount: Double?, val isEstimate: Boolean = false, val uncertainty: Double? = null) {
    fun times(other: DoseQuantity): DoseQuantity {
        validateDose(amount, uncertainty)
        validateDose(other.amount, other.uncertainty)
        val estimate = isEstimate || other.isEstimate
        val a = amount ?: return DoseQuantity(null, estimate)
        val b = other.amount ?: return DoseQuantity(null, estimate)
        val sa = if (isEstimate) uncertainty ?: 0.0 else 0.0
        val sb = if (other.isEstimate) other.uncertainty ?: 0.0 else 0.0
        // Independent product variance, without subtracting nearly equal large squares.
        val sigma = hypot(hypot(a * sb, b * sa), sa * sb)
        val result = DoseQuantity(a * b, estimate, sigma.takeIf { it > 0.0 })
        validateDose(result.amount, result.uncertainty)
        return result
    }
}

data class ResolvedDose(
    val subject: SubjectId,
    val name: String,
    val units: String?,
    val quantity: DoseQuantity,
    val componentPath: List<Int>,
    val isHiddenInTimeline: Boolean,
)

/** Immutable snapshot shared by accounting and presentation; hiding never removes leaves. */
class CustomDefinitionGraph(
    definitions: List<CustomUnit>,
    val components: List<DoseComponent>,
    val roaInfos: List<CustomRoaInfo>,
) {
    val definitions = definitions.associateBy { it.id }
    private val children = components.groupBy { it.parentCustomUnitId }
        .mapValues { (_, rows) -> rows.sortedWith(compareBy<DoseComponent> { it.creationDate }.thenBy { it.id }) }
    init { require(this.definitions.size == definitions.size) { "Duplicate custom definition IDs." } }

    fun validateFrom(id: Int) {
        val selected = linkedSetOf<Int>()
        fun collect(current: Int, depth: Int = 1) {
            require(depth <= 64) { "Definitions may be nested at most 64 levels." }
            require(selected.size <= 100000) { "Too many nested definitions." }
            if (!selected.add(current)) return
            children[current].orEmpty().mapNotNull { it.childCustomUnitId }.forEach { collect(it, depth + 1) }
        }
        collect(id)
        require(selected.all { it in definitions }) { "A nested definition is missing." }
        val graph = CustomDefinitionGraph(selected.map { definitions.getValue(it) }, components.filter { it.parentCustomUnitId in selected },
            roaInfos.filter { it.customUnitId in selected })
        graph.validate()
        graph.resolve(id, DoseQuantity(1.0))
    }

    fun validate() {
        require(definitions.size <= 100000 && components.size <= 1000000) { "Too many custom definitions or components." }
        val ids = components.filter { it.id != 0 }.map { it.id }
        require(ids.distinct().size == ids.size) { "Duplicate component IDs." }
        definitions.values.forEach {
            require(it.id > 0 && it.name.isNotBlank() && it.unit.isNotBlank()) { "Enter a name and singular unit for every definition." }
            validateDose(it.dose, it.estimatedDoseStandardDeviation)
            validateDose(it.halfToleranceInDays, it.zeroToleranceInDays)
            require(it.halfToleranceInDays == null || it.zeroToleranceInDays == null || it.halfToleranceInDays <= it.zeroToleranceInDays) {
                "Time to zero tolerance must not be shorter than time to half tolerance."
            }
        }
        components.forEach {
            require(it.parentCustomUnitId in definitions) { "A component's parent definition is missing." }
            require((it.substanceName != null) xor (it.childCustomUnitId != null)) { "Choose exactly one substance or nested definition for each component." }
            require(it.substanceName == null || it.substanceName.isNotBlank() && (it.dose == null || !it.substanceUnit.isNullOrBlank())) { "Enter the component's substance and measurement unit for a known amount." }
            require(it.childCustomUnitId == null || it.childCustomUnitId in definitions) { "A nested definition is missing." }
            validateDose(it.dose, it.estimatedDoseStandardDeviation)
        }
        require(roaInfos.map { it.customUnitId to it.administrationRoute }.distinct().size == roaInfos.size) { "A route is defined more than once." }
        roaInfos.forEach { info ->
            require(info.customUnitId in definitions) { "Route information refers to a missing definition." }
            validateRoaInfo(info)
        }
        val visiting = mutableSetOf<Int>()
        val depths = mutableMapOf<Int, Int>()
        fun depth(id: Int): Int {
            depths[id]?.let { return it }
            require(visiting.add(id)) { "A definition cannot contain itself, including through another blend." }
            require(visiting.size <= 64) { "Definitions may be nested at most 64 levels." }
            val value = 1 + children[id].orEmpty().mapNotNull { it.childCustomUnitId }.maxOfOrNull { depth(it) }.orZero()
            visiting.remove(id)
            require(value <= 64) { "Definitions may be nested at most 64 levels." }
            depths[id] = value
            return value
        }
        definitions.keys.forEach(::depth)
    }

    fun resolve(id: Int, quantity: DoseQuantity): List<ResolvedDose> {
        val result = mutableListOf<ResolvedDose>()
        val visiting = mutableSetOf<Int>()
        fun visit(unitId: Int, amount: DoseQuantity, path: List<Int>, hidden: Boolean) {
            require(visiting.add(unitId) && visiting.size <= 64) { "This definition contains a cycle or is nested too deeply. Edit its components." }
            val unit = definitions[unitId] ?: error("This definition is missing. Reopen the entry and choose another definition.")
            val hide = hidden || unit.isHiddenInTimeline
            val parts = children[unitId].orEmpty()
            if (parts.isEmpty()) {
                val name = unit.substanceName
                result += if (name != null) ResolvedDose(SubjectId.Catalog(name), name, unit.originalUnit,
                    amount.times(DoseQuantity(unit.dose, unit.isEstimate, unit.estimatedDoseStandardDeviation)), path, hide)
                else ResolvedDose(SubjectId.Definition(unitId), unit.name, unit.unit, amount, path, hide)
            } else parts.forEach { part ->
                val combined = amount.times(DoseQuantity(part.dose, part.isEstimate, part.estimatedDoseStandardDeviation))
                val nextPath = path + part.id
                val child = part.childCustomUnitId
                if (child != null) visit(child, combined, nextPath, hide || part.isHiddenInTimeline)
                else {
                    val name = part.substanceName ?: error("A component has no substance or nested definition.")
                    result += ResolvedDose(SubjectId.Catalog(name), name, part.substanceUnit, combined, nextPath, hide || part.isHiddenInTimeline)
                }
                require(result.size <= 100000) { "This definition expands into too many components." }
            }
            visiting.remove(unitId)
        }
        validateDose(quantity.amount, quantity.uncertainty)
        visit(id, quantity, emptyList(), false)
        return result
    }

    fun availableRoutes(id: Int, catalogRoutes: (String) -> List<AdministrationRoute>): List<AdministrationRoute> {
        val visiting = mutableSetOf<Int>()
        fun routes(unitId: Int): List<AdministrationRoute> {
            require(visiting.add(unitId) && visiting.size <= 64) { "This definition contains a cycle." }
            val unit = definitions[unitId] ?: error("Nested definition is missing.")
            val own = roaInfos.filter { it.customUnitId == unitId }.map { it.administrationRoute }
            val found = when {
                own.isNotEmpty() -> own
                unit.administrationRoute != null -> listOf(unit.administrationRoute)
                else -> children[unitId].orEmpty().flatMap { part ->
                    part.childCustomUnitId?.let(::routes) ?: part.substanceName?.let(catalogRoutes).orEmpty()
                }
            }
            visiting.remove(unitId)
            return found.distinct()
        }
        return routes(id)
    }
}

private fun Int?.orZero() = this ?: 0

fun validateRoaInfo(info: CustomRoaInfo) {
    val thresholds = listOf(info.lightMinDose, info.commonMinDose, info.strongMinDose, info.heavyMinDose)
    thresholds.forEach { validateDose(it, null) }
    require(thresholds.filterNotNull().zipWithNext().all { (a, b) -> a <= b }) { "Dose thresholds must be in increasing order." }
    listOf(Triple(info.onsetMin, info.onsetMax, info.onsetUnit), Triple(info.comeupMin, info.comeupMax, info.comeupUnit),
        Triple(info.peakMin, info.peakMax, info.peakUnit), Triple(info.offsetMin, info.offsetMax, info.offsetUnit)).forEach { (min, max, unit) ->
        if (min != null || max != null || unit != null) {
            require(min != null && max != null && unit in setOf("SECONDS", "MINUTES", "HOURS", "DAYS")) { "Enter a minimum, maximum and time unit for each duration range, or leave all three blank." }
            validateDose(min, max)
            require(min <= max) { "A duration minimum must not exceed its maximum." }
        }
    }
}
