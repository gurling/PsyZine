// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.customunit

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.ChooseDoseCustomUnitRoute
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.search.suggestion.models.CustomUnitDose
import com.isaakhanimann.journal.ui.tabs.settings.customunits.CustomUnitEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChooseDoseCustomUnitViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    substanceRepo: SubstanceRepository,
    state: SavedStateHandle,
    sessionGuard: JournalSessionGuard,
) : CustomUnitEditorViewModel(sessionGuard, state) {
    var customUnit by mutableStateOf<CustomUnit?>(null); private set
    var doseRemark by mutableStateOf<String?>(null); private set
    init {
        val id = state.toRoute<ChooseDoseCustomUnitRoute>().customUnitId
        load {
            val record = experienceRepo.getCustomUnit(id)
                ?: throw CustomUnitProblem("This custom unit was deleted. Go back and choose another unit.")
            val expected = savedState.get<String>("selectedUnitDefinition")
            if (expected != null && expected != record.definitionFingerprint()) throw CustomUnitProblem("This custom unit changed. Go back and choose a dose again.")
            customUnit = record.copy()
            savedState["selectedUnitDefinition"] = record.definitionFingerprint()
            val substance = substanceRepo.getSubstance(record.substanceName)
            doseRemark = substance?.dosageRemark
            roaDose = substance?.getRoa(record.administrationRoute)?.roaDose
        }
    }
    val estimatedDoseDeviation get() = estimatedDoseStandardDeviation
    fun onDoseTextChange(value: String) = onChangeOfDose(value)
    fun onEstimatedDoseDeviationChange(value: String) = onChangeOfEstimatedDoseDeviation(value)
    private val calculation get() = dose?.let { amount -> customUnit?.let { CustomUnitDose(amount, isEstimate, if (isEstimate) estimatedDoseDeviation else null, it) } }
    override val currentDoseClass get() = calculation?.calculatedDose?.takeIf { it.isFinite() && it >= 0 }?.let { roaDose?.getDoseClass(it) }
    val isValidDose get() = customUnit != null && inputErrors().isEmpty()
    override fun inputErrors(): Map<CustomUnitField, String> = buildMap {
        numericInputError(doseText, "Dose", required = true)?.let { put(CustomUnitField.DOSE, it) }
        if (isEstimate) numericInputError(estimatedDoseDeviationText, "Uncertainty")?.let { put(CustomUnitField.UNCERTAINTY, it) }
        if (isEmpty()) calculation?.let { value ->
            if (value.calculatedDose?.isFinite() == false || value.calculatedDoseStandardDeviation?.isFinite() == false)
                put(CustomUnitField.DOSE, "The calculated dose or uncertainty is too large. Enter a smaller amount or correct the custom unit.")
        }
    }
    val customUnitCalculationText get() = calculation?.takeIf { inputErrors().isEmpty() }?.let {
        it.doseDescription + " × " + it.customUnit.getDoseOfOneUnitDescription() + " = " + (it.calculatedDoseDescription ?: "Unknown dose")
    }
    fun next(unknown: Boolean = false, navigate: (CustomUnit, Double?, Double?, Boolean, String, String) -> Unit) {
        if (!canSave) return
        if (!unknown) { showErrors(inputErrors()); if (errors.isNotEmpty()) return }
        val selected = customUnit ?: return
        val amount = if (unknown) null else dose
        val estimate = !unknown && isEstimate
        val uncertainty = if (estimate) estimatedDoseDeviation else null
        operation("use this unit", { navigate(selected, amount, uncertainty, estimate, checkNotNull(generation), selected.definitionFingerprint()) }) {
            val current = experienceRepo.getCustomUnit(selected.id)
                ?: throw CustomUnitProblem("This custom unit was deleted. Go back and choose another unit.")
            if (current.definitionFingerprint() != selected.definitionFingerprint()) throw CustomUnitProblem("This custom unit changed. Go back and choose a dose again.")
            if (current.isArchived) throw CustomUnitProblem("This unit is archived. Go back and choose an available unit, or unarchive it in Settings.")
            if (customUnitInputErrors(current.name, current.unit, current.originalUnit,
                    current.dose?.toString() ?: "", current.estimatedDoseStandardDeviation?.toString() ?: "", true).isNotEmpty())
                throw CustomUnitProblem("This custom unit has invalid saved values. Correct it in Settings → Custom units before using it.")
            try { validateDose(amount, uncertainty); validateCustomUnitDose(amount, uncertainty, estimate, current) }
            catch (_: IllegalArgumentException) { throw CustomUnitProblem("The calculated dose or uncertainty is too large. Enter a smaller amount or correct the custom unit.", requiresReopen = false) }
        }
    }
}

/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */
