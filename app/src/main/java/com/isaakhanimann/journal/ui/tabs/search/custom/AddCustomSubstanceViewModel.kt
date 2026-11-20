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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AddCustomSubstanceViewModel @Inject constructor(
    val experienceRepo: ExperienceRepository,
    private val guard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    private val state: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, guard, state)
    init { session.load {} }

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

    fun initializeName(initialName: String) {
        if (!state.contains("customSubstanceName")) name = initialName
    }

    val isValid get() = name.isNotBlank() && units.isNotBlank()

    fun addCustomSubstance(onDone: (customSubstanceName: String) -> Unit) {
        if (!isValid) return
        val customSubstance = CustomSubstance(name = name, units = units, description = description)
        session.run(onSuccess = { onDone(customSubstance.name) }) {
            guard.withSession(session.generation) {
                experienceRepo.insert(customSubstance)
            }
        }
    }
}
