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

package com.isaakhanimann.journal.ui.tabs.journal.experience.rating.add

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
import com.isaakhanimann.journal.ui.main.navigation.graphs.AddRatingRoute
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class AddRatingViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    private val state: SavedStateHandle
) : ViewModel() {
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)
    var selectedRating by mutableStateOf(ShulginRatingOption.TWO_PLUS)
    var isThisOverallRating by mutableStateOf(false)
    fun changeOverallRating(value: Boolean) {
        if (!session.ready || session.busy) return
        isThisOverallRating = value
        state["ratingOverall"] = value
    }
    var isHiddenInTimeline by mutableStateOf(false)
        private set
    fun changeHiddenInTimeline(value: Boolean) {
        if (!session.ready || session.busy) return
        isHiddenInTimeline = value
        state["ratingHiddenInTimeline"] = value
    }
    val experienceId = AddRatingRoute(state.journalRecordId("experienceId")).experienceId
    var localDateTimeFlow = MutableStateFlow(LocalDateTime.now())

    var isThereAlreadyAnOverallRatingFlow = experienceRepo.getRatingsFlow(experienceId).map { ratings ->
        ratings.any { it.time == null }
    }.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    init {
        session.load {
            isHiddenInTimeline = state.get<Boolean>("ratingHiddenInTimeline") ?: false
            val experience = experienceRepo.getExperience(id = experienceId) ?: error("This entry was deleted. Close this screen.")
            val isOldExperience = experience.sortDate.isBefore(Instant.now().minus(12, ChronoUnit.HOURS))
            if (isOldExperience) {
                localDateTimeFlow.value = experience.sortDate.getLocalDateTime()
            }
            selectedRating = ShulginRatingOption.entries.firstOrNull { it.name == state.get<String>("ratingOption") } ?: selectedRating
            isThisOverallRating = state.get<Boolean>("ratingOverall") ?: false
            localDateTimeFlow.value = state.get<String>("ratingTime")?.let { LocalDateTime.parse(it) } ?: localDateTimeFlow.value
            state["ratingOption"] = selectedRating.name
            state["ratingOverall"] = isThisOverallRating
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
        selectedRating = newRating
        state["ratingOption"] = newRating.name
    }

    fun onDoneTap(onSuccess: () -> Unit) {
        if (!session.ready || session.busy) return
        val newRating = ShulginRating(
            time = if (isThisOverallRating) null else localDateTimeFlow.value.getInstant(),
            creationDate = Instant.now(),
            option = selectedRating,
            experienceId = experienceId,
            isHiddenInTimeline = isHiddenInTimeline,
        )
        session.run(onSuccess) {
            experienceRepo.insert(newRating, session.generation)
        }
    }
}
