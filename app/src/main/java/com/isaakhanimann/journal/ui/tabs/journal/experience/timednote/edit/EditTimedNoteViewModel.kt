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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timednote.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.TimedNote
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditTimedNoteRoute
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EditTimedNoteViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    state: SavedStateHandle
) : ViewModel() {
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)
    var note by mutableStateOf("")
    var color by mutableStateOf(AdaptiveColor.BLUE)
    var isPartOfTimeline by mutableStateOf(true)
    private var loadedInstant: java.time.Instant = java.time.Instant.now()
    private var loadedLocal: LocalDateTime = loadedInstant.getLocalDateTime()
    var localDateTimeFlow = MutableStateFlow(loadedLocal)
    private var timedNote: TimedNote? = null
    private val editTimedNoteRoute = state.toRoute<EditTimedNoteRoute>()
    val experienceId = editTimedNoteRoute.experienceId

    private val timedNoteId: Int

    init {
        val timedNoteId = editTimedNoteRoute.timedNoteId
        this.timedNoteId = timedNoteId
        session.load {
            val loadedNote = experienceRepo.getTimedNote(id = timedNoteId) ?: error("This entry was deleted. Close this screen.")
            check(loadedNote.experienceId == experienceId && experienceRepo.getExperience(experienceId) != null) { "This note or experience was deleted." }
            timedNote = loadedNote
            loadedNote.time.let { time ->
                loadedInstant = time
                loadedLocal = time.getLocalDateTime()
                localDateTimeFlow.emit(loadedLocal)
            }
            note = loadedNote.note
            color = loadedNote.color
            isPartOfTimeline = loadedNote.isPartOfTimeline
        }
    }


    fun onChangeTime(newLocalDateTime: LocalDateTime) {
        viewModelScope.launch {
            localDateTimeFlow.emit(newLocalDateTime)
        }
    }

    fun onChangeIsPartOfTimeline(newIsPartOfTimeline: Boolean) {
        isPartOfTimeline = newIsPartOfTimeline
    }

    fun onChangeNote(newNote: String) {
        note = newNote
    }

    fun onChangeColor(newColor: AdaptiveColor) {
        color = newColor
    }

    private val ingestionsFlow = experienceRepo.getIngestionsWithCompanionsFlow(experienceId)
    private val timedNotesFlow = experienceRepo.getTimedNotesFlowSorted(experienceId)

    val alreadyUsedColorsFlow: StateFlow<List<AdaptiveColor>> =
        ingestionsFlow.combine(timedNotesFlow) { ingestions, notes ->
            val companionColors = ingestions.mapNotNull { it.substanceCompanion?.color }
            val noteColors = notes.map { it.color }
            return@combine (companionColors + noteColors).distinct()
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

    fun delete(onSuccess: () -> Unit) = session.run(onSuccess) {
        experienceRepo.delete(checkNotNull(timedNote), session.generation)
    }

    fun onDoneTap(onSuccess: () -> Unit) = session.run(onSuccess) {
        check(note.isNotBlank()) { "Enter a note before saving." }
        val updated = checkNotNull(timedNote).copy(time = com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeFlow.value, loadedLocal, loadedInstant), note = note, color = color, isPartOfTimeline = isPartOfTimeline)
        experienceRepo.update(updated, session.generation)
    }
}
