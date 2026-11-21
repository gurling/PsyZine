/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.search.custom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditCustomSubstanceRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EditCustomSubstanceViewModel @Inject constructor(
    val experienceRepo: ExperienceRepository,
    private val sessionGuard: JournalSessionGuard,
    private val state: SavedStateHandle
) : ViewModel() {

    val id = state.toRoute<EditCustomSubstanceRoute>().customSubstanceId
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)
    private var draftName by mutableStateOf(state.get<String>("customSubstanceName") ?: "")
    private var draftUnits by mutableStateOf(state.get<String>("customSubstanceUnits") ?: "")
    private var draftDescription by mutableStateOf(state.get<String>("customSubstanceDescription") ?: "")

    var name: String
        get() = draftName
        set(value) {
            draftName = value
            state["customSubstanceName"] = value
        }
    var units: String
        get() = draftUnits
        set(value) {
            draftUnits = value
            state["customSubstanceUnits"] = value
        }
    var description: String
        get() = draftDescription
        set(value) {
            draftDescription = value
            state["customSubstanceDescription"] = value
        }

    val isValid get() = session.ready && !session.busy && name.isNotBlank() && units.isNotBlank()

    init {
        session.load {
            val substance = checkNotNull(experienceRepo.getCustomSubstance(id)) {
                "This custom substance was deleted. Close this screen."
            }
            if (!state.contains("customSubstanceName")) name = substance.name
            if (!state.contains("customSubstanceUnits")) units = substance.units
            if (!state.contains("customSubstanceDescription")) description = substance.description
        }
    }

    fun onDoneTap(dismiss: () -> Unit) {
        if (!isValid) return
        val substance = CustomSubstance(id, name, units, description)
        session.run(onSuccess = dismiss) {
            sessionGuard.withSession(session.generation) {
                requireCurrentSubstance()
                experienceRepo.update(substance)
            }
        }
    }

    fun deleteCustomSubstance(dismiss: () -> Unit) {
        val substance = CustomSubstance(id, name, units, description)
        session.run(onSuccess = dismiss) {
            sessionGuard.withSession(session.generation) {
                requireCurrentSubstance()
                experienceRepo.delete(substance)
            }
        }
    }

    private suspend fun requireCurrentSubstance() {
        if (experienceRepo.getCustomSubstance(id) == null) {
            throw com.isaakhanimann.journal.data.room.StaleJournalSession()
        }
    }
}
