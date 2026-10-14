// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.customunits.add

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.FinishAddCustomUnitRoute
import com.isaakhanimann.journal.ui.tabs.settings.customunits.CustomUnitEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FinishAddCustomUnitViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val substanceRepository: SubstanceRepository,
    sessionGuard: JournalSessionGuard,
    state: SavedStateHandle
) : CustomUnitEditorViewModel(sessionGuard, state) {
    val journalGeneration get() = generation
    private val route = state.toRoute<FinishAddCustomUnitRoute>()
    init {
        administrationRoute = route.administrationRoute
        substanceName = route.substanceName
        load {
            // Room may run before the composition that created this editor has
            // applied its state writes. Navigation inputs are immutable here.
            val substance = substanceRepository.getSubstance(route.substanceName)
            val customSubstance = experienceRepo.getCustomSubstance(route.substanceName)
            if (substance == null && customSubstance == null) throw CustomUnitProblem("This substance no longer exists. Go back and choose a substance again.")
            val referenceDose = substance?.getRoa(route.administrationRoute)?.roaDose
            roaDose = referenceDose
            if (savedState.get<Boolean>("unitDraft.initialized") != true) {
                originalUnit = referenceDose?.units ?: customSubstance?.units ?: "mg"
                savedState["unitDraft.initialized"] = true
            }
            isUnitsFieldShown = referenceDose?.units.isNullOrBlank() && customSubstance?.units.isNullOrBlank() || originalUnit.isBlank()
        }
    }
    fun createSaveAndDismissAfter(dismiss: (Int) -> Unit) {
        if (!canSave || !validate()) return
        val candidate = CustomUnit(substanceName = route.substanceName, administrationRoute = route.administrationRoute,
            name = name.trim(), unit = unit.trim(), unitPlural = unitPlural.trim().ifBlank { null },
            originalUnit = originalUnit.trim(), dose = dose, isEstimate = isEstimate,
            estimatedDoseStandardDeviation = if (isEstimate) estimatedDoseStandardDeviation else null,
            isArchived = isArchived, note = note)
        var id = 0
        operation("save this unit", { dismiss(id) }) {
            if (substanceRepository.getSubstance(route.substanceName) == null && experienceRepo.getCustomSubstance(route.substanceName) == null)
                throw CustomUnitProblem("This substance was deleted. Go back and choose a substance again.")
            id = experienceRepo.insert(candidate)
        }
    }
}

/*
 * Copyright (c) 2024. Isaak Hanimann.
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
