// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.definitions

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.numericInputError
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toPreservedString
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ComponentDraft(val key: String, val id: Int = 0, val substance: String? = null,
    val childId: Int? = null, val units: String = "", val dose: String = "", val estimate: Boolean = false,
    val uncertainty: String = "", val note: String = "", val hidden: Boolean = false, val createdAt: Long,
    val noteWasNull: Boolean = false, val unitsWereNull: Boolean = false)

@Serializable
data class RoaDraft(val key: String, val id: Int = 0, val route: AdministrationRoute,
    val fields: Map<String, String> = emptyMap(), val createdAt: Long)

@Serializable
data class DefinitionDraft(val name: String = "", val unit: String = "", val plural: String = "", val note: String = "",
    val color: String = "", val archived: Boolean = false, val hidden: Boolean = false,
    val showComponents: Boolean = false, val showTolerances: Boolean = false,
    val halfDays: String = "", val zeroDays: String = "", val native: Boolean = true,
    val components: List<ComponentDraft> = emptyList(), val roas: List<RoaDraft> = emptyList()) {
    fun errors(): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "Enter a name for this definition.")
        if (unit.isBlank()) put("unit", "Enter a singular unit, for example capsule.")
        numericInputError(halfDays, "Time to half tolerance")?.let { put("halfDays", it) }
        numericInputError(zeroDays, "Time to zero tolerance")?.let { put("zeroDays", it) }
        if (halfDays.number() != null && zeroDays.number() != null && halfDays.number()!! > zeroDays.number()!!) {
            put("zeroDays", "Time to zero tolerance must not be shorter than time to half tolerance.")
        }
        components.forEach { component ->
            numericInputError(component.dose, "Amount")?.let { put("${component.key}.dose", it) }
            numericInputError(component.uncertainty, "Uncertainty")?.let { put("${component.key}.uncertainty", it) }
            if (component.substance != null && component.dose.isNotBlank() && component.units.isBlank()) put("${component.key}.units", "Enter the component's measurement unit, for example mg.")
        }
        roas.forEach { roa ->
            roa.fields.filterKeys { !it.endsWith("Unit") }.forEach { (key, text) ->
                numericInputError(text, "Value")?.let { put("${roa.key}.$key", it) }
            }
            try { validateRoaInfo(roa.toEntity(1)) }
            catch (e: IllegalArgumentException) { put("${roa.key}.range", e.message ?: "Check the dose and duration ranges.") }
        }
    }

    fun content(original: CustomUnit?, createdAt: Instant): DefinitionWithContent {
        require(errors().isEmpty()) { "Correct the highlighted fields before saving." }
        val base = original ?: CustomUnit(0, null, name, createdAt, null, null, null, false, false, unit, null, null, "")
        val candidate = base.copy(name = name, unit = unit,
            unitPlural = if (plural == base.unitPlural.orEmpty()) base.unitPlural else plural.ifBlank { null }, note = note,
            substanceName = if (native) null else base.substanceName,
            administrationRoute = if (native) null else base.administrationRoute,
            dose = if (native) null else base.dose,
            estimatedDoseStandardDeviation = if (native) null else base.estimatedDoseStandardDeviation,
            originalUnit = if (native) null else base.originalUnit,
            isEstimate = if (native) false else base.isEstimate,
            color = if (color == base.color.orEmpty()) base.color else color.ifBlank { null }, isArchived = archived, isHiddenInTimeline = hidden,
            areTimelinesOfSubcomponentsShown = showComponents, areTolerancesOfSubcomponentsShown = showTolerances,
            halfToleranceInDays = halfDays.number(), zeroToleranceInDays = zeroDays.number())
        return DefinitionWithContent(candidate, components.map {
            DoseComponent(it.id, base.id, it.substance, if (it.childId != null || it.unitsWereNull && it.units.isEmpty()) null else it.units, it.childId,
                it.dose.number(), it.estimate, it.uncertainty.number(), if (it.noteWasNull && it.note.isEmpty()) null else it.note, Instant.ofEpochMilli(it.createdAt), it.hidden)
        }, roas.map { it.toEntity(base.id) })
    }
}

private fun String.number(): Double? = replace(',', '.').toDoubleOrNull()
private fun Double?.editor() = this?.toPreservedString().orEmpty()

fun DefinitionWithContent.toDraft() = DefinitionDraft(unit.name, unit.unit, unit.unitPlural.orEmpty(), unit.note,
    unit.color.orEmpty(), unit.isArchived, unit.isHiddenInTimeline, unit.areTimelinesOfSubcomponentsShown,
    unit.areTolerancesOfSubcomponentsShown, unit.halfToleranceInDays.editor(), unit.zeroToleranceInDays.editor(),
    unit.substanceName == null,
    components.ifEmpty { listOfNotNull(unit.legacyComponent()) }.sortedWith(compareBy<DoseComponent> { it.creationDate }.thenBy { it.id }).map {
        ComponentDraft("component:${it.id}", it.id, it.substanceName, it.childCustomUnitId, it.substanceUnit.orEmpty(),
            it.dose.editor(), it.isEstimate, it.estimatedDoseStandardDeviation.editor(), it.note.orEmpty(), it.isHiddenInTimeline, it.creationDate.toEpochMilli(), it.note == null, it.substanceUnit == null)
    }, roaInfos.map { info ->
        RoaDraft("route:${info.id}", info.id, info.administrationRoute, mapOf(
            "lightMinDose" to info.lightMinDose.editor(), "commonMinDose" to info.commonMinDose.editor(),
            "strongMinDose" to info.strongMinDose.editor(), "heavyMinDose" to info.heavyMinDose.editor(),
            "onsetMin" to info.onsetMin.editor(), "onsetMax" to info.onsetMax.editor(), "onsetUnit" to info.onsetUnit.orEmpty(),
            "comeupMin" to info.comeupMin.editor(), "comeupMax" to info.comeupMax.editor(), "comeupUnit" to info.comeupUnit.orEmpty(),
            "peakMin" to info.peakMin.editor(), "peakMax" to info.peakMax.editor(), "peakUnit" to info.peakUnit.orEmpty(),
            "offsetMin" to info.offsetMin.editor(), "offsetMax" to info.offsetMax.editor(), "offsetUnit" to info.offsetUnit.orEmpty()), info.creationDate.toEpochMilli())
    })

private fun RoaDraft.toEntity(parent: Int): CustomRoaInfo {
    fun n(key: String) = fields[key].orEmpty().number()
    fun u(key: String) = fields[key]?.takeIf { it.isNotBlank() }
    return CustomRoaInfo(id, parent, route, Instant.ofEpochMilli(createdAt), n("lightMinDose"), n("commonMinDose"), n("strongMinDose"), n("heavyMinDose"),
        n("onsetMin"), n("onsetMax"), u("onsetUnit"), n("comeupMin"), n("comeupMax"), u("comeupUnit"),
        n("peakMin"), n("peakMax"), u("peakUnit"), n("offsetMin"), n("offsetMax"), u("offsetUnit"))
}
