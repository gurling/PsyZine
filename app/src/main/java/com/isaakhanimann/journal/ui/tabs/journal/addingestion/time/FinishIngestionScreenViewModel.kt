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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.definitions.definitionFingerprint
import com.isaakhanimann.journal.data.definitions.toGraph
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.StomachFullness
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestions
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.FinishIngestionRoute
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import com.isaakhanimann.journal.ui.utils.getStringOfPattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.isaakhanimann.journal.data.room.*

const val hourLimitToSeparateIngestions: Long = 12

enum class IngestionTimePickerOption {
    POINT_IN_TIME, TIME_RANGE
}

@HiltViewModel
class FinishIngestionScreenViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val userPreferences: UserPreferences,
    private val sessionGuard: JournalSessionGuard,
    private val state: SavedStateHandle,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) : ViewModel() {
    val recentTimePresets = userPreferences.journalDisplaySettingsFlow.map { it.recentTimePresets }
        .catch { if (it is java.io.IOException) emit(false) else throw it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), false)
    var savingTimeInputPreference by mutableStateOf(false)
        private set
    fun setRecentTimePresets(value: Boolean) {
        if (isSaving || savingTimeInputPreference) return
        savingTimeInputPreference = true
        viewModelScope.launch {
            try { userPreferences.saveRecentTimePresets(value) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { saveError = "The time-entry preference could not be saved. Your entered times are unchanged. Please try again." }
            finally { savingTimeInputPreference = false }
        }
    }
    var timeAdjustmentMessage by mutableStateOf<String?>(null)
        private set
    var isUpdatingTime by mutableStateOf(false)
        private set
    var timeUpdateFailed by mutableStateOf(false)
        private set
    private var timeUpdateVersion = 0
    private var timeUpdateJob: kotlinx.coroutines.Job? = null
    private fun updateTime(action: suspend () -> Unit) {
        if (isSaving || isLoadingColor) return
        val version = ++timeUpdateVersion
        timeUpdateJob?.cancel()
        isUpdatingTime = true
        timeUpdateJob = viewModelScope.launch {
            try { action(); timeUpdateFailed = false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                timeUpdateFailed = true
                saveError = "The time could not be applied. Choose the time again before saving."
            }
            finally { if (timeUpdateVersion == version) isUpdatingTime = false }
        }
    }
    private var expectedDefinitionFingerprint: String? = null
    private var nativeDefinition = false
    private var generation: String? = null
    private var expectedUnitFingerprint: String? = null
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    fun dismissSaveError() { saveError = null }
    var substanceName by mutableStateOf("")
    private var presetStart = clock.instant()
    private var presetEnd = presetStart.plus(30, ChronoUnit.MINUTES)
    private var loadedStartLocal = presetStart.getLocalDateTime()
    private var loadedEndLocal = presetEnd.getLocalDateTime()
    val localDateTimeStartFlow = MutableStateFlow(loadedStartLocal)
    val localDateTimeEndFlow = MutableStateFlow(loadedEndLocal)
    private fun selectedStartInstant() = com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeStartFlow.value, loadedStartLocal, presetStart)
    private fun selectedEndInstant() = com.isaakhanimann.journal.ui.utils.preservedInstant(localDateTimeEndFlow.value, loadedEndLocal, presetEnd)
    val ingestionTimePickerOptionFlow = MutableStateFlow(IngestionTimePickerOption.POINT_IN_TIME)
    val experiencesInRangeFlow = MutableStateFlow<List<ExperienceWithIngestions>>(emptyList())
    val selectedExperienceFlow = MutableStateFlow<ExperienceWithIngestions?>(null)
    private var suggestedExperienceStart: Instant? = null
    var enteredTitle by mutableStateOf(LocalDateTime.now().getStringOfPattern("dd MMMM yyyy"))
    val isEnteredTitleOk get() = enteredTitle.isNotEmpty()
    var consumerName by mutableStateOf("")


    fun onChangeTimePickerOption(ingestionTimePickerOption: IngestionTimePickerOption) =
        viewModelScope.launch {
            ingestionTimePickerOptionFlow.emit(ingestionTimePickerOption)
        }

    private val sortedExperiencesFlow = experienceRepo.getSortedExperiencesWithIngestionsFlow()

    val sortedConsumerNamesFlow =
        experienceRepo.observeConsumerNames().stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    var isLoadingColor by mutableStateOf(true)
    var isShowingColorPicker by mutableStateOf(false)
    var selectedColor by mutableStateOf(AdaptiveColor.BLUE)
    var note by mutableStateOf("")
    var stomachFullness by mutableStateOf<StomachFullness?>(null)
        private set
    fun changeStomachFullness(value: StomachFullness?) {
        if (isSaving || isLoadingColor) return
        stomachFullness = value
        state["stomachFullness"] = value?.serialized
    }
    private var hasTitleBeenChanged = false

    fun changeTitle(newTitle: String) {
        enteredTitle = newTitle
        hasTitleBeenChanged = true
    }

    fun changeConsumerName(newName: String) {
        consumerName = newName
    }

    val previousNotesFlow: StateFlow<List<String>> =
        experienceRepo.getSortedIngestionsFlow(substanceName, limit = 10).map { list ->
            list.mapNotNull { it.notes }.filter { it.isNotBlank() }.distinct()
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val administrationRoute: AdministrationRoute
    private val dose: Double?
    private val units: String?
    private val isEstimate: Boolean
    private val estimatedDoseStandardDeviation: Double?
    private val customUnitId: Int?
    private var substanceCompanion: SubstanceCompanion? = null

    private val companionFlow = experienceRepo.getAllSubstanceCompanionsFlow()

    val alreadyUsedColorsFlow: StateFlow<List<AdaptiveColor>> =
        companionFlow.map { companions ->
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

    init {
        val finishIngestionRoute = state.toRoute<FinishIngestionRoute>()
        substanceName = finishIngestionRoute.substanceName
        administrationRoute = finishIngestionRoute.administrationRoute
        dose = finishIngestionRoute.dose
        estimatedDoseStandardDeviation = finishIngestionRoute.estimatedDoseStandardDeviation
        customUnitId = finishIngestionRoute.customUnitId
        expectedUnitFingerprint = finishIngestionRoute.customUnitFingerprint
        expectedDefinitionFingerprint = finishIngestionRoute.definitionFingerprint
        units = finishIngestionRoute.units?.let {
            if (it == "null") {
                null
            } else {
                it
            }
        }
        isEstimate = finishIngestionRoute.isEstimate
        viewModelScope.launch {
            try {
                generation = sessionGuard.readSession(state.get<String>("journalGeneration") ?: finishIngestionRoute.journalGeneration) { token -> token }.also { state["journalGeneration"] = it }
                stomachFullness = com.isaakhanimann.journal.data.room.experiences.stomachFullnessSelection(state.get<String>("stomachFullness"))
                val lastIngestionTimeOfExperience =
                    userPreferences.lastIngestionTimeOfExperienceFlow.first().takeUnless { finishIngestionRoute.startAtNow }
                val clonedIngestionTime = userPreferences.clonedIngestionTimeFlow.first().takeUnless { finishIngestionRoute.startAtNow }
                if (clonedIngestionTime != null) {
                    presetStart = clonedIngestionTime
                    presetEnd = clonedIngestionTime.plus(30, ChronoUnit.MINUTES)
                    localDateTimeStartFlow.emit(clonedIngestionTime.getLocalDateTime())
                    localDateTimeEndFlow.emit(clonedIngestionTime.plus(30, ChronoUnit.MINUTES).getLocalDateTime())
                    updateTitleBasedOnTime(clonedIngestionTime)
                } else if (lastIngestionTimeOfExperience != null) {
                    val wasLastIngestionOfExperienceMoreThan20HoursAgo =
                        lastIngestionTimeOfExperience < Instant.now().minus(20, ChronoUnit.HOURS)
                    if (wasLastIngestionOfExperienceMoreThan20HoursAgo) {
                        presetStart = lastIngestionTimeOfExperience
                        presetEnd = lastIngestionTimeOfExperience.plus(30, ChronoUnit.MINUTES)
                        localDateTimeStartFlow.emit(lastIngestionTimeOfExperience.getLocalDateTime())
                        localDateTimeEndFlow.emit(lastIngestionTimeOfExperience.plus(30, ChronoUnit.MINUTES).getLocalDateTime())
                        updateTitleBasedOnTime(lastIngestionTimeOfExperience)
                    }
                }
                loadedStartLocal = localDateTimeStartFlow.value
                loadedEndLocal = localDateTimeEndFlow.value
                updateExperiencesBasedOnSelectedTime()
                val allCompanions = experienceRepo.getAllSubstanceCompanionsFlow().first()
                nativeDefinition = customUnitId?.let { experienceRepo.getCustomUnit(it)?.substanceName == null } == true
                val thisCompanion = allCompanions.firstOrNull { it.substanceName == substanceName }
                substanceCompanion = thisCompanion
                if (nativeDefinition) {
                    isShowingColorPicker = false
                } else if (thisCompanion == null) {
                    isShowingColorPicker = true
                    val alreadyUsedColors = allCompanions.map { it.color }
                    val otherColors = AdaptiveColor.entries.filter { !alreadyUsedColors.contains(it) }
                    selectedColor = otherColors.filter { it.isPreferred }.randomOrNull()
                        ?: otherColors.randomOrNull() ?: AdaptiveColor.entries.random()
                } else {
                    selectedColor = thisCompanion.color
                }
                isLoadingColor = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = if (customUnitId != null) customUnitFailure(e, "load the journal") else if (e is IllegalStateException) e.message else "Could not load the journal. Close this screen and try again."
            }
        }
    }

    fun onChangeOfSelectedExperience(experienceWithIngestions: ExperienceWithIngestions?) =
        viewModelScope.launch {
            selectedExperienceFlow.emit(experienceWithIngestions)
        }

    fun onChangeStartDateOrTime(newLocalDateTime: LocalDateTime) = updateTime { changeStartDateOrTime(newLocalDateTime) }

    fun selectRecentStart(preset: com.isaakhanimann.journal.data.preferences.RecentIngestionTime) {
        val time = preset.at(clock)
        updateTime {
            presetStart = time
            loadedStartLocal = time.getLocalDateTime()
            changeStartDateOrTime(loadedStartLocal)
        }
    }
    fun selectRecentEnd(preset: com.isaakhanimann.journal.data.preferences.RecentIngestionTime) {
        val time = preset.at(clock)
        updateTime {
            presetEnd = time
            loadedEndLocal = time.getLocalDateTime()
            changeEndDateOrTime(loadedEndLocal)
        }
    }
    private suspend fun changeStartDateOrTime(newLocalDateTime: LocalDateTime) {
        timeAdjustmentMessage = null
        localDateTimeStartFlow.emit(newLocalDateTime)
        updateExperiencesBasedOnSelectedTime()
        val startTime = selectedStartInstant()
        if (!hasTitleBeenChanged) {
            updateTitleBasedOnTime(startTime)
        }
        val endTime = selectedEndInstant()
        if (startTime > endTime || Duration.between(startTime, endTime).toHours() > 24) {
            val newEndTime = startTime.plus(30, ChronoUnit.MINUTES)
            presetEnd = newEndTime
            loadedEndLocal = newEndTime.getLocalDateTime()
            localDateTimeEndFlow.emit(loadedEndLocal)
            if (ingestionTimePickerOptionFlow.value == IngestionTimePickerOption.TIME_RANGE)
                timeAdjustmentMessage = "End time was moved to 30 minutes after the selected start."
        }
    }

    fun onChangeEndDateOrTime(newLocalDateTime: LocalDateTime) = updateTime { changeEndDateOrTime(newLocalDateTime) }
    private suspend fun changeEndDateOrTime(newLocalDateTime: LocalDateTime) {
        timeAdjustmentMessage = null
        localDateTimeEndFlow.emit(newLocalDateTime)
        val endTime = selectedEndInstant()
        val startTime =
            selectedStartInstant()
        if (startTime > endTime) {
            val newStartTime = endTime.minus(30, ChronoUnit.MINUTES)
            presetStart = newStartTime
            loadedStartLocal = newStartTime.getLocalDateTime()
            localDateTimeStartFlow.emit(loadedStartLocal)
            timeAdjustmentMessage = "Start time was moved to 30 minutes before the selected end."
        }
        if (selectedStartInstant() != suggestedExperienceStart) updateExperiencesBasedOnSelectedTime()
        if (!hasTitleBeenChanged) updateTitleBasedOnTime(selectedStartInstant())
    }

    private fun updateTitleBasedOnTime(time: Instant) {
        enteredTitle = time.getStringOfPattern("dd MMMM yyyy")
    }

    private suspend fun updateExperiencesBasedOnSelectedTime() {
        val selectedInstant = selectedStartInstant()
        val fromInstant = selectedInstant.minus(3, ChronoUnit.DAYS)
        val toInstant = selectedInstant.plus(1, ChronoUnit.DAYS)
        val experiencesInRange =
            experienceRepo.getSortedExperiencesWithIngestionsWithSortDateBetween(
                fromInstant = fromInstant,
                toInstant = toInstant
            )
        experiencesInRangeFlow.emit(experiencesInRange)
        val closestExperience = experiencesInRange.firstOrNull { experience ->
            val sortedIngestions = experience.ingestions.sortedBy { it.time }
            val firstIngestionTime =
                sortedIngestions.firstOrNull()?.time ?: return@firstOrNull false
            val upperBoundBasedOnFirstIngestion = firstIngestionTime.plus(15, ChronoUnit.HOURS)
            val lastIngestionTime = sortedIngestions.lastOrNull()?.time ?: return@firstOrNull false
            val upperBoundBasedOnLastIngestion = lastIngestionTime.plus(3, ChronoUnit.HOURS)
            val finalUpperBound =
                maxOf(upperBoundBasedOnFirstIngestion, upperBoundBasedOnLastIngestion)
            val lowerBound = firstIngestionTime.minus(3, ChronoUnit.HOURS)
            return@firstOrNull selectedInstant in lowerBound..finalUpperBound
        }
        selectedExperienceFlow.emit(closestExperience)
        suggestedExperienceStart = selectedInstant
    }

    fun createSaveAndDismissAfter(dismiss: () -> Unit) {
        if (isSaving || isLoadingColor || isUpdatingTime || timeUpdateFailed) return
        isSaving = true
        viewModelScope.launch {
            try {
                sessionGuard.withSession(generation) { createAndSaveIngestion() }
                dismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = if (customUnitId != null) customUnitFailure(e, "save this ingestion") else if (e is IllegalStateException) e.message else "Could not save this ingestion. Try again."
            } finally { isSaving = false }
        }
    }

    private suspend fun createAndSaveIngestion() {
        com.isaakhanimann.journal.data.room.validateDose(dose, estimatedDoseStandardDeviation)
        customUnitId?.let { id ->
            val unit = experienceRepo.getCustomUnit(id) ?: throw CustomUnitProblem("This custom unit was deleted. Go back and choose a dose again.")
            if (expectedUnitFingerprint != null && expectedUnitFingerprint != unit.definitionFingerprint()) throw CustomUnitProblem("This custom unit changed. Go back and choose a dose again.")
            nativeDefinition = unit.substanceName == null
            if (nativeDefinition) {
                val graph = experienceRepo.definitionContents()
                if (expectedDefinitionFingerprint == null || graph.definitionFingerprint(id) != expectedDefinitionFingerprint) throw CustomUnitProblem("This definition changed. Go back and choose an amount again.")
                graph.toGraph().resolve(id, com.isaakhanimann.journal.data.definitions.DoseQuantity(dose, isEstimate, estimatedDoseStandardDeviation))
            } else if (unit.substanceName != substanceName || unit.administrationRoute != administrationRoute) throw CustomUnitProblem("This custom unit changed. Go back and choose a dose again.")
            try { if (!nativeDefinition) validateCustomUnitDose(dose, estimatedDoseStandardDeviation, isEstimate, unit) }
            catch (_: IllegalArgumentException) { throw CustomUnitProblem("This dose cannot be calculated with the custom unit. Go back and correct the amount or the unit in Settings.") }
        }
        val substanceCompanion = if (nativeDefinition) null else SubstanceCompanion(
            substanceName,
            color = selectedColor
        )
        val oldIdToUse = selectedExperienceFlow.firstOrNull()?.experience?.id
        if (oldIdToUse == null) {
            val ingestionTime =
                selectedStartInstant()
            val newExperience = Experience(
                id = 0,
                title = enteredTitle,
                text = "",
                creationDate = Instant.now(),
                sortDate = ingestionTime,
                location = null // todo: allow to add real location
            )
            val newIngestion = createNewIngestion(newExperience.id)
            experienceRepo.insertIngestionExperienceAndCompanion(
                ingestion = newIngestion,
                experience = newExperience,
                substanceCompanion = substanceCompanion
            )
        } else {
            check(experienceRepo.getExperience(oldIdToUse) != null) { "This experience was deleted. Close this screen and reopen it." }
            val newIngestion = createNewIngestion(oldIdToUse)
            experienceRepo.insertIngestionAndCompanion(
                ingestion = newIngestion,
                substanceCompanion = substanceCompanion
            )
        }
    }

    private suspend fun createNewIngestion(experienceId: Int): Ingestion {
        val time = selectedStartInstant()
        val ingestionTimePickerOption = ingestionTimePickerOptionFlow.first()
        val endTime = when (ingestionTimePickerOption) {
            IngestionTimePickerOption.POINT_IN_TIME -> null
            IngestionTimePickerOption.TIME_RANGE -> selectedEndInstant()
        }
        require(endTime == null || endTime >= time) { "End time must not be before start time." }
        return Ingestion(
            substanceName = if (nativeDefinition) null else substanceName,
            time = time,
            endTime = endTime,
            administrationRoute = administrationRoute,
            dose = dose,
            isDoseAnEstimate = isEstimate,
            estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
            units = units,
            experienceId = experienceId,
            notes = note,
            stomachFullness = stomachFullness,
            consumerName = consumerName.ifBlank {
                null
            },
            customUnitId = customUnitId
        )
    }
}
