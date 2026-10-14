// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.customunits.edit

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditCustomUnitRoute
import com.isaakhanimann.journal.ui.tabs.settings.customunits.CustomUnitEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EditCustomUnitViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    substanceRepository: SubstanceRepository,
    sessionGuard: JournalSessionGuard,
    state: SavedStateHandle
) : CustomUnitEditorViewModel(sessionGuard, state) {
    private val id = state.toRoute<EditCustomUnitRoute>().customUnitId
    init {
        load {
            val snapshot = experienceRepo.getCustomUnitWithIngestions(id)
                ?: throw CustomUnitProblem("This custom unit was deleted. Close this screen and choose another unit.")
            val record = snapshot.customUnit
            val expected = savedState.get<String>("unitDraft.original")
            if (expected != null && expected != record.editorFingerprint()) throw CustomUnitProblem("This unit changed while you were editing. Close this screen and reopen it before saving.")
            original = record.copy()
            savedState["unitDraft.original"] = record.editorFingerprint()
            substanceName = record.substanceName; administrationRoute = record.administrationRoute
            roaDose = substanceRepository.getSubstance(substanceName)?.getRoa(administrationRoute)?.roaDose
            val customSubstance = experienceRepo.getCustomSubstance(substanceName)
            numberOfIngestionsWithThisCustomUnit = snapshot.ingestions.size
            populate(record)
            isUnitsFieldShown = roaDose?.units.isNullOrBlank() && customSubstance?.units.isNullOrBlank() || originalUnit.isBlank()
        }
    }
    private suspend fun current(): CustomUnit {
        val record = experienceRepo.getCustomUnit(id)
            ?: throw CustomUnitProblem("This custom unit was deleted. Close this screen and choose another unit.")
        if (record != original) throw CustomUnitProblem("This unit changed while you were editing. Close this screen and reopen it before saving.")
        return record
    }
    fun updateAndDismissAfter(dismiss: () -> Unit) {
        if (!canSave || !validate()) return
        val candidate = candidate(checkNotNull(original))
        operation("save this unit", dismiss) {
            val previous = current()
            if (candidate.dose != previous.dose || candidate.estimatedDoseStandardDeviation != previous.estimatedDoseStandardDeviation || candidate.isEstimate != previous.isEstimate) {
                experienceRepo.getCustomUnitWithIngestions(id)?.ingestions?.forEach { ingestion ->
                    if (ingestion.dose?.let { it.isFinite() && it >= 0 } == true &&
                        (ingestion.estimatedDoseStandardDeviation == null || ingestion.estimatedDoseStandardDeviation?.let { it.isFinite() && it >= 0 } == true)) {
                        try { validateCustomUnitDose(ingestion.dose, ingestion.estimatedDoseStandardDeviation, ingestion.isDoseAnEstimate, candidate) }
                        catch (_: IllegalArgumentException) { throw CustomUnitProblem("This change would make a dose or uncertainty in your existing journal too large to calculate. Enter smaller values for this unit.", requiresReopen = false) }
                    }
                }
            }
            experienceRepo.update(candidate)
        }
    }
    fun deleteCustomUnit(dismiss: () -> Unit) {
        operation("delete this unit", dismiss) { experienceRepo.delete(current()) }
    }
    /** Archive only the saved definition; invalid legacy fields and unsaved edits are never rewritten. */
    fun archiveSavedUnit(dismiss: () -> Unit) {
        operation("archive this unit", dismiss) { experienceRepo.update(current().copy(isArchived = true)) }
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
