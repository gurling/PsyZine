/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditIngestionRoute
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.IngestionTimePickerOption
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toPreservedString
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class EditIngestionViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val userPreferences: UserPreferences,
    private val sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    state: SavedStateHandle
) : ViewModel() {
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)
    private var ingestionFlow: MutableStateFlow<Ingestion?> = MutableStateFlow(null)
    var ingestion: Ingestion? = null
    private var loadedStart: LocalDateTime? = null
    private var loadedEnd: LocalDateTime? = null
    private var initialEndInstant: java.time.Instant? = null
    var note by mutableStateOf("")
    var isEstimate by mutableStateOf(false)
    var isKnown by mutableStateOf(true)
    var dose by mutableStateOf("")
    var estimatedDoseStandardDeviation by mutableStateOf("")
    var units by mutableStateOf("")
    var experienceId by mutableIntStateOf(1)
    val ingestionTimePickerOptionFlow = MutableStateFlow(IngestionTimePickerOption.POINT_IN_TIME)
    var localDateTimeStartFlow = MutableStateFlow(LocalDateTime.now())
    var localDateTimeEndFlow = MutableStateFlow(LocalDateTime.now())
    var consumerName by mutableStateOf("")
    private var selectedCustomUnitId: Int? = null
    var customUnit: CustomUnit? by mutableStateOf(null)
    val otherCustomUnits = experienceRepo.getAllCustomUnitsFlow().combine(ingestionFlow) { customUnits, ing ->
        customUnits.filter {customUnit ->
            customUnit.administrationRoute == ing?.administrationRoute && customUnit.substanceName == ing.substanceName && customUnit.id != ing.customUnitId
        }
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun onDoseChange(newDoseText: String) {
        dose = newDoseText
    }

    fun onChangeEstimatedDoseStandardDeviation(newEstimatedDoseStandardDeviation: String) {
        estimatedDoseStandardDeviation = newEstimatedDoseStandardDeviation
    }

    init {
        val editIngestionRoute = state.toRoute<EditIngestionRoute>()
        session.load {
            val ingestionAndCustomUnit =
                experienceRepo.getIngestionFlow(id = editIngestionRoute.ingestionId).first() ?: error("This entry was deleted. Close this screen.")
            val ing = ingestionAndCustomUnit.ingestion
            check(experienceRepo.getExperience(ing.experienceId) != null) { "This experience was deleted." }
            ingestionFlow.emit(ing)
            ingestion = ing
            note = ing.notes ?: ""
            isEstimate = ing.isDoseAnEstimate
            estimatedDoseStandardDeviation = ing.estimatedDoseStandardDeviation?.toPreservedString() ?: ""
            experienceId = ing.experienceId
            dose = ing.dose?.toPreservedString() ?: ""
            isKnown = ing.dose != null
            units = ing.units ?: ""
            consumerName = ing.consumerName ?: ""
            loadedStart = ing.time.getLocalDateTime()
            localDateTimeStartFlow.emit(checkNotNull(loadedStart))
            val endTime = ing.endTime
            if (endTime != null) {
                ingestionTimePickerOptionFlow.emit(IngestionTimePickerOption.TIME_RANGE)
                localDateTimeEndFlow.emit(endTime.getLocalDateTime())
            } else {
                localDateTimeEndFlow.emit(ing.time.plus(30, ChronoUnit.MINUTES).getLocalDateTime())
            }
            selectedCustomUnitId = ing.customUnitId
            customUnit = ingestionAndCustomUnit.customUnit
            loadedEnd = localDateTimeEndFlow.value
            initialEndInstant = ing.endTime ?: ing.time.plus(30, ChronoUnit.MINUTES)
        }
    }

    val sortedConsumerNamesFlow =
        experienceRepo.getSortedIngestions(limit = 200).map { ingestions ->
            return@map ingestions.mapNotNull { it.consumerName }.distinct()
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    fun onChangeCustomUnit(newCustomUnit: CustomUnit?) {
        selectedCustomUnitId = newCustomUnit?.id
        customUnit = newCustomUnit
        newCustomUnit?.unit?.let {
            units = it
        }
    }

    fun onChangeTimePickerOption(ingestionTimePickerOption: IngestionTimePickerOption) =
        viewModelScope.launch {
            ingestionTimePickerOptionFlow.emit(ingestionTimePickerOption)
        }

    fun onChangeStartTime(newLocalDateTime: LocalDateTime) = viewModelScope.launch {
        localDateTimeStartFlow.emit(newLocalDateTime)
        val startTime = newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val endTime = localDateTimeEndFlow.first().atZone(ZoneId.systemDefault()).toInstant()
        if (startTime > endTime) {
            val newEndTime = startTime.plus(30, ChronoUnit.MINUTES)
            localDateTimeEndFlow.emit(newEndTime.getLocalDateTime())
        }
    }

    fun onChangeEndTime(newLocalDateTime: LocalDateTime) = viewModelScope.launch {
        localDateTimeEndFlow.emit(newLocalDateTime)
        val endTime = newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val startTime =
            localDateTimeStartFlow.first().atZone(ZoneId.systemDefault()).toInstant()
        if (startTime > endTime) {
            val newStartTime = endTime.minus(30, ChronoUnit.MINUTES)
            localDateTimeStartFlow.emit(newStartTime.getLocalDateTime())
        }
    }

    fun onChangeConsumerName(newName: String) {
        consumerName = newName
    }

    fun toggleIsKnown() {
        isKnown = isKnown.not()
    }

    fun onChangeIsEstimate(newIsEstimate: Boolean) {
        isEstimate = newIsEstimate
    }

    fun saveClonedIngestionTime(onSuccess: () -> Unit) = session.run(onSuccess) {
        sessionGuard.withSession(session.generation) {
            val current = checkNotNull(ingestion)
            check(experienceRepo.getExperience(current.experienceId) != null) { "This experience was deleted." }
            val stored = experienceRepo.getIngestion(current.id)
            check(stored != null && stored.experienceId == current.experienceId) { "This ingestion was deleted or moved. Reopen it." }
            userPreferences.saveClonedIngestionTime(stored.time)
        }
    }

    val relevantExperiences: StateFlow<List<ExperienceOption>> = localDateTimeStartFlow.map {
        val selectedInstant = it.getInstant()
        val fromDate = selectedInstant.minus(2, ChronoUnit.DAYS)
        val toDate = selectedInstant.plus(2, ChronoUnit.DAYS)
        return@map experienceRepo.getIngestionsWithExperiencesFlow(fromDate, toDate).firstOrNull()
            ?: emptyList()
    }.map { list ->
        return@map list.map {
            ExperienceOption(id = it.experience.id, title = it.experience.title)
        }.distinct()
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun onDoneTap(onSuccess: () -> Unit) = session.run(onSuccess) {
        val current = checkNotNull(ingestion)
        // Imported records may retain uncertainty even when the estimate flag is false.
        // A note-only edit must preserve it; changing the flag to false is an explicit edit.
        val retainUncertainty = isEstimate || isEstimate == current.isDoseAnEstimate
        com.isaakhanimann.journal.data.room.validateDoseText(dose, estimatedDoseStandardDeviation, known = isKnown, estimate = retainUncertainty)
        val updated = current.copy(notes = if (note == current.notes.orEmpty()) current.notes else note, isDoseAnEstimate = isEstimate, experienceId = experienceId,
            dose = if (isKnown) dose.toDoubleOrNull() else null,
            estimatedDoseStandardDeviation = if (retainUncertainty) estimatedDoseStandardDeviation.toDoubleOrNull() else null,
            units = if (units == current.units.orEmpty()) current.units else units, customUnitId = selectedCustomUnitId,
            time = com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeStartFlow.value, loadedStart, current.time),
            endTime = if (ingestionTimePickerOptionFlow.value == IngestionTimePickerOption.TIME_RANGE)
                com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeEndFlow.value, loadedEnd, initialEndInstant) else null,
            consumerName = if (consumerName == current.consumerName.orEmpty()) current.consumerName else consumerName.ifBlank { null })
        experienceRepo.update(updated, current.experienceId, session.generation)
    }

    fun deleteIngestion(onSuccess: () -> Unit) = session.run(onSuccess) {
        experienceRepo.delete(checkNotNull(ingestion), session.generation)
    }
}

data class ExperienceOption(
    val id: Int,
    val title: String
)