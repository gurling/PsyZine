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

package com.isaakhanimann.journal.ui.tabs.settings.customunits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomUnitsViewModel @Inject constructor(experienceRepository: ExperienceRepository, state: SavedStateHandle) : ViewModel() {
    val searchTextFlow = state.getStateFlow("customUnitSearch", "")
    private val retries = MutableStateFlow(0)
    fun onSearch(text: String) { savedState["customUnitSearch"] = text }
    private val savedState = state
    fun retry() { retries.value++ }
    val unitsState = retries.flatMapLatest { experienceRepository.getCustomUnitsFlow(false).unitListState() }
        .combine(searchTextFlow) { result, search ->
            val query = search.trim()
            result.copy(units = result.units.filter { custom -> query.isBlank() ||
                listOf(custom.name, custom.substanceName, custom.unit, custom.unitPlural ?: "", custom.note)
                    .any { it.contains(query, ignoreCase = true) } })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomUnitListState())
}
