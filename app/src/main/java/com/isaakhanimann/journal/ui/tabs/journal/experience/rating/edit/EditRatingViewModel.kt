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

package com.isaakhanimann.journal.ui.tabs.journal.experience.rating.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditRatingRoute
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EditRatingViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    state: SavedStateHandle
) : ViewModel() {
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)
    private val ratingId: Int
    var selectedRatingOption by mutableStateOf(ShulginRatingOption.TWO_PLUS)
    private var loadedInstant: java.time.Instant = java.time.Instant.now()
    private var loadedLocal: LocalDateTime = loadedInstant.getLocalDateTime()
    var localDateTimeFlow = MutableStateFlow(loadedLocal)
    var rating: ShulginRating? = null
    var isOverallRatingFlow = MutableStateFlow(false)

    init {
        val editRatingRoute = state.toRoute<EditRatingRoute>()
        val ratingId = editRatingRoute.ratingId
        this.ratingId = ratingId
        session.load {
            val loadedRating = experienceRepo.getRating(id = ratingId) ?: error("This entry was deleted. Close this screen.")
            check(experienceRepo.getExperience(loadedRating.experienceId) != null) { "This experience was deleted." }
            rating = loadedRating
            loadedRating.time?.also { time ->
                loadedInstant = time
                loadedLocal = time.getLocalDateTime()
                localDateTimeFlow.emit(loadedLocal)
            } ?: run {
                isOverallRatingFlow.value = true
            }
            selectedRatingOption = loadedRating.option
        }
    }

    fun onChangeTime(newLocalDateTime: LocalDateTime) {
        viewModelScope.launch {
            localDateTimeFlow.emit(newLocalDateTime)
        }
    }

    fun onChangeRating(newRating: ShulginRatingOption) {
        selectedRatingOption = newRating
    }

    fun delete(onSuccess: () -> Unit) = session.run(onSuccess) {
        experienceRepo.delete(checkNotNull(rating), session.generation)
    }

    fun onDoneTap(onSuccess: () -> Unit) = session.run(onSuccess) {
        val current = checkNotNull(rating)
        val updated = current.copy(time = if (isOverallRatingFlow.value) null else com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeFlow.value, loadedLocal, loadedInstant), option = selectedRatingOption)
        experienceRepo.update(updated, session.generation)
    }
}
