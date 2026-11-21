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
import com.isaakhanimann.journal.ui.utils.journalRecordId
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditRatingRoute
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EditRatingViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    private val state: SavedStateHandle
) : ViewModel() {
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)
    private val ratingId: Int
    var selectedRatingOption by mutableStateOf(ShulginRatingOption.TWO_PLUS)
    var isHiddenInTimeline by mutableStateOf(false)
        private set
    fun changeHiddenInTimeline(value: Boolean) {
        if (!session.ready || session.busy) return
        isHiddenInTimeline = value
        state["ratingHiddenInTimeline"] = value
    }
    private var loadedInstant: java.time.Instant = java.time.Instant.now()
    private var loadedLocal: LocalDateTime = loadedInstant.getLocalDateTime()
    var localDateTimeFlow = MutableStateFlow(loadedLocal)
    var rating: ShulginRating? = null
    var isOverallRatingFlow = MutableStateFlow(false)

    init {
        val editRatingRoute = EditRatingRoute(state.journalRecordId("ratingId"))
        val ratingId = editRatingRoute.ratingId
        this.ratingId = ratingId
        session.load {
            val loadedRating = experienceRepo.getRating(id = ratingId) ?: error("This entry was deleted. Close this screen.")
            check(experienceRepo.getExperience(loadedRating.experienceId) != null) { "This experience was deleted." }
            rating = loadedRating
            isHiddenInTimeline = state.get<Boolean>("ratingHiddenInTimeline") ?: loadedRating.isHiddenInTimeline
            loadedRating.time?.also { time ->
                loadedInstant = time
                loadedLocal = time.getLocalDateTime()
                localDateTimeFlow.value = loadedLocal
            } ?: run {
                isOverallRatingFlow.value = true
            }
            selectedRatingOption = ShulginRatingOption.entries.firstOrNull { it.name == state.get<String>("ratingOption") } ?: loadedRating.option
            localDateTimeFlow.value = state.get<String>("ratingTime")?.let { LocalDateTime.parse(it) } ?: localDateTimeFlow.value
            state["ratingOption"] = selectedRatingOption.name
            state["ratingTime"] = localDateTimeFlow.value.toString()
            state["ratingHiddenInTimeline"] = isHiddenInTimeline
        }
    }

    fun onChangeTime(newLocalDateTime: LocalDateTime) {
        if (!session.ready || session.busy) return
        localDateTimeFlow.value = newLocalDateTime
        state["ratingTime"] = newLocalDateTime.toString()
    }

    fun onChangeRating(newRating: ShulginRatingOption) {
        if (!session.ready || session.busy) return
        selectedRatingOption = newRating
        state["ratingOption"] = newRating.name
    }

    fun delete(onSuccess: () -> Unit) = session.run(onSuccess) {
        experienceRepo.delete(checkNotNull(rating), session.generation)
    }

    fun onDoneTap(onSuccess: () -> Unit) {
        if (!session.ready || session.busy) return
        val current = checkNotNull(rating)
        val updated = current.copy(time = if (isOverallRatingFlow.value) null else com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeFlow.value, loadedLocal, loadedInstant),
            option = selectedRatingOption, isHiddenInTimeline = isHiddenInTimeline)
        session.run(onSuccess) {
            experienceRepo.update(updated, session.generation)
        }
    }
}
