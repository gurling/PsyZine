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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCustomSubstanceViewModel @Inject constructor(
    val experienceRepo: ExperienceRepository,
    private val sessionGuard: JournalSessionGuard,
    state: SavedStateHandle
) : ViewModel() {

    var id = 0
    var name by mutableStateOf("")
    var units by mutableStateOf("")
    var description by mutableStateOf("")
    private var generation: String? = null
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    fun dismissSaveError() { saveError = null }

    val isValid get() = id > 0 && name.isNotBlank() && units.isNotBlank() && !isSaving

    init {
        val editCustomSubstanceRoute = state.toRoute<EditCustomSubstanceRoute>()
        val customSubstanceId = editCustomSubstanceRoute.customSubstanceId
        viewModelScope.launch {
            try {
                generation = state.get<String>("journalGeneration") ?: sessionGuard.currentGeneration().also { state["journalGeneration"] = it }
                val customSubstance = sessionGuard.withSession(generation) { experienceRepo.getCustomSubstance(customSubstanceId) }
                checkNotNull(customSubstance) { "This custom substance was deleted. Close this screen." }
                id = customSubstanceId
                name = customSubstance.name
                units = customSubstance.units
                description = customSubstance.description
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = if (e is IllegalStateException) e.message else "Could not load this substance. Close this screen and try again."
            }
        }
    }

    fun onDoneTap(dismiss: () -> Unit) {
        if (isValid) mutate(dismiss) {
            check(experienceRepo.getCustomSubstance(id) != null) { "This custom substance was deleted. Close this screen." }
            experienceRepo.update(CustomSubstance(id, name, units, description))
        }
    }

    fun deleteCustomSubstance(dismiss: () -> Unit) {
        if (id > 0 && !isSaving) mutate(dismiss) { experienceRepo.delete(CustomSubstance(id, name, units, description)) }
    }

    private fun mutate(dismiss: () -> Unit, action: suspend () -> Unit) {
        isSaving = true
        viewModelScope.launch {
            try {
                sessionGuard.withSession(generation, action)
                dismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = if (e is IllegalStateException) e.message else "Could not save changes. Try again."
            } finally { isSaving = false }
        }
    }
}
