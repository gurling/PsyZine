// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room

fun String.nonnegativeDoubleOrNull(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

/** Blank optional fields represent unknown values; malformed values never do. */
fun validateDose(dose: Double?, uncertainty: Double?) {
    require(dose == null || dose.isFinite() && dose >= 0.0) { "Dose must be a finite, nonnegative number." }
    require(uncertainty == null || uncertainty.isFinite() && uncertainty >= 0.0) { "Uncertainty must be a finite, nonnegative number." }
}

fun validateDoseText(dose: String, uncertainty: String, known: Boolean = true, estimate: Boolean = true) {
    require(!known || dose.isBlank() || dose.nonnegativeDoubleOrNull() != null) { "Dose must be a finite, nonnegative number." }
    require(!estimate || uncertainty.isBlank() || uncertainty.nonnegativeDoubleOrNull() != null) { "Uncertainty must be a finite, nonnegative number." }
}

fun validateCustomUnitDose(dose: Double?, uncertainty: Double?, estimate: Boolean,
    unit: com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit) {
    validateDose(unit.dose, unit.estimatedDoseStandardDeviation)
    if (dose != null) {
        val calculated = com.isaakhanimann.journal.ui.tabs.journal.addingestion.search.suggestion.models.CustomUnitDose(dose, estimate, uncertainty, unit)
        validateDose(calculated.calculatedDose, calculated.calculatedDoseStandardDeviation)
    }
}
