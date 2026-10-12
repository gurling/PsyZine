/*
 * Copyright (c) 2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.settings.colors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubstanceColorsViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository,
    private val guard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    state: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, guard, state)
    val substanceCompanionsFlow = experienceRepository.getAllSubstanceCompanionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    init { session.load { experienceRepository.deleteUnusedSubstanceCompanions() } }

    fun updateColor(color: AdaptiveColor, substanceName: String) = session.run {
        guard.withSession(session.generation) {
            val companion = experienceRepository.getAllSubstanceCompanions().firstOrNull { it.substanceName == substanceName }
            check(companion != null) { "This substance color was deleted. Close this screen." }
            experienceRepository.update(companion.copy(color=color))
        }
    }

    val alreadyUsedColorsFlow: StateFlow<List<AdaptiveColor>> =
        substanceCompanionsFlow.map { companions ->
            companions.map { it.color }.distinct()
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val otherColorsFlow: StateFlow<List<AdaptiveColor>> =
        alreadyUsedColorsFlow.map { alreadyUsedColors ->
            AdaptiveColor.entries.filter {
                !alreadyUsedColors.contains(it)
            }
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

}