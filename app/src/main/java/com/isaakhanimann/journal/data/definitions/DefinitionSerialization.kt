// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.ui.tabs.settings.CustomUnitSerializable

fun CustomUnitSerializable.toEntity() = CustomUnit(id, substanceName, name, creationDate,
    administrationRoute, dose, estimatedDoseStandardDeviation, isEstimate, isArchived, unit,
    unitPlural, originalUnit, note, color, isHiddenInTimeline, areTimelinesOfSubcomponentsShown,
    areTolerancesOfSubcomponentsShown, halfToleranceInDays, zeroToleranceInDays)

fun CustomUnit.toPortable(components: List<DoseComponent>, roas: List<CustomRoaInfo>) = CustomUnitSerializable(
    id, substanceName, name, creationDate, administrationRoute, dose, estimatedDoseStandardDeviation,
    isEstimate, isArchived, unit, unitPlural, originalUnit, note, color, isHiddenInTimeline,
    areTimelinesOfSubcomponentsShown, areTolerancesOfSubcomponentsShown, halfToleranceInDays,
    zeroToleranceInDays, components, roas)

fun CustomUnit.legacyComponent() = substanceName?.let {
    DoseComponent(parentCustomUnitId = id, substanceName = it, substanceUnit = originalUnit,
        dose = dose, isEstimate = isEstimate, estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
        note = note, creationDate = creationDate)
}
