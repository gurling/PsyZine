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

package com.isaakhanimann.journal.ui.tabs.journal.experience

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.isaakhanimann.journal.ui.utils.journalRecordId
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.ExperienceRoute
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.InteractionChecker
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.hourLimitToSeparateIngestions
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.ConsumerWithIngestions
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.CumulativeDose
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.CumulativeRouteAndDose
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.IngestionElement
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.InteractionExplanation
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.DataForOneRating
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.DataForOneTimedNote
import com.isaakhanimann.journal.ui.tabs.settings.combinations.CombinationSettingsStorage
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.CumulativeProjection
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.projectedCumulativeDoses
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PresentedIngestion
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.TimelineModelFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExperienceViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    val attachments: com.isaakhanimann.journal.data.attachments.AttachmentRepository,
    private val substanceRepo: SubstanceRepository,
    private val interactionChecker: InteractionChecker,
    private val userPreferences: UserPreferences,
    combinationSettingsStorage: CombinationSettingsStorage,
    private val sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard,
    records: com.isaakhanimann.journal.data.room.experiences.JournalRecordRepository,
    presentation: com.isaakhanimann.journal.data.preferences.PresentationPreferences,
    private val notificationCoordinator: com.isaakhanimann.journal.service.ActiveExperienceCoordinator,
    state: SavedStateHandle
) : ViewModel() {
    val notificationSettings = presentation.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0),
        com.isaakhanimann.journal.data.preferences.PresentationSettings())
    var notificationBusy by androidx.compose.runtime.mutableStateOf(false)
        private set
    var notificationError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    fun clearNotificationError() { notificationError = null }
    fun changeLiveNotification(stop: Boolean) {
        if (!session.ready || session.busy || notificationBusy) return
        val generation = session.generation ?: return
        notificationBusy = true
        viewModelScope.launch {
            try {
                if (stop) notificationCoordinator.stopNotification()
                else notificationCoordinator.startExperience(experienceId, generation)
                notificationError = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: com.isaakhanimann.journal.service.NotificationActionException) { notificationError = e.message }
            catch (_: Exception) { notificationError = "The live notification could not be updated. Your journal is unchanged. Please try again." }
            finally { notificationBusy = false }
        }
    }
    var displayPreferenceError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var savingDisplayPreference by androidx.compose.runtime.mutableStateOf(false)
        private set
    fun clearDisplayPreferenceError() { displayPreferenceError = null }
    fun saveHideTimelineQuantities(value: Boolean) {
        if (savingDisplayPreference) return
        savingDisplayPreference = true
        viewModelScope.launch {
            try { userPreferences.saveHideTimelineQuantities(value); displayPreferenceError = null }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { displayPreferenceError = "The timeline display preference could not be saved. Please try again." }
            finally { savingDisplayPreference = false }
        }
    }
    val session = com.isaakhanimann.journal.ui.utils.JournalEditorSession(viewModelScope, sessionGuard, state)

    private val areSubstanceHeightsIndependentFlow =
        userPreferences.areSubstanceHeightsIndependentFlow.stateIn(
            initialValue = true,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0)
        )

    val isTimelineHiddenFlow = userPreferences.isTimelineHiddenFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0)
    )

    fun saveTimeDisplayOption(savedTimeDisplayOption: SavedTimeDisplayOption) {
        viewModelScope.launch {
            userPreferences.saveTimeDisplayOption(savedTimeDisplayOption)
        }
    }

    val isOralTimelineDisclaimerHidden = userPreferences.isOralDisclaimerHiddenFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0)
    )

    val areDosageDotsHiddenFlow = userPreferences.areDosageDotsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0)
    )

    fun saveOralDisclaimerIsHidden(isOralDisclaimerHidden: Boolean) {
        viewModelScope.launch {
            userPreferences.saveOralDisclaimerIsHidden(isOralDisclaimerHidden)
        }
    }

    private val experienceId: Int

    init {
        val experienceRoute = ExperienceRoute(state.journalRecordId("experienceId"))
        val expId = experienceRoute.experienceId
        experienceId = expId
        session.load {
            checkNotNull(experienceRepo.getExperience(expId)) { "This experience was deleted. Close this screen." }
        }
    }

    fun saveIsFavorite(isFavorite: Boolean) {
        session.run {
            experienceRepo.updateFavorite(experienceId, isFavorite, session.generation)
        }
    }

    val recordState = com.isaakhanimann.journal.ui.utils.journalReadSession(viewModelScope, state) { generation ->
        records.observeTimeline(experienceId, generation)
    }
    val photos = recordState.map { it.value?.photos.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val ingestionsWithCompanionsFlow = recordState.map { it.value?.ingestions.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val ratingsFlow = recordState.map { it.value?.ratings.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val timedNotesSortedFlow = recordState.map { it.value?.timedNotes.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val experienceFlow = recordState.map { it.value?.experience }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    private val currentTimeFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(timeMillis = 1000 * 10)
        }
    }

    val isCurrentExperienceFlow =
        ingestionsWithCompanionsFlow.combine(currentTimeFlow) { ingestionsWithCompanions, currentTime ->
            val ingestionTimes =
                ingestionsWithCompanions.map { it.ingestion.time }
            val lastIngestionTime = ingestionTimes.maxOrNull() ?: return@combine false
            val limitAgo = currentTime.minus(hourLimitToSeparateIngestions, ChronoUnit.HOURS)
            return@combine limitAgo < lastIngestionTime
        }.stateIn(
            initialValue = false,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0)
        )

    val savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.stateIn(
        initialValue = SavedTimeDisplayOption.REGULAR,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0)
    )

    val timeDisplayOptionFlow =
        userPreferences.savedTimeDisplayOptionFlow.combine(isCurrentExperienceFlow) { savedOption: SavedTimeDisplayOption, isCurrentExperience: Boolean ->
            when (savedOption) {
                SavedTimeDisplayOption.AUTO -> if (isCurrentExperience) TimeDisplayOption.RELATIVE_TO_NOW else TimeDisplayOption.REGULAR
                SavedTimeDisplayOption.RELATIVE_TO_NOW -> TimeDisplayOption.RELATIVE_TO_NOW
                SavedTimeDisplayOption.RELATIVE_TO_START -> TimeDisplayOption.RELATIVE_TO_START
                SavedTimeDisplayOption.TIME_BETWEEN -> TimeDisplayOption.TIME_BETWEEN
                SavedTimeDisplayOption.REGULAR -> TimeDisplayOption.REGULAR
            }
        }.stateIn(
            initialValue = TimeDisplayOption.REGULAR,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0)
        )

    private data class EffectsSnapshot(
        val byConsumer: Map<String?, List<PresentedIngestion>> = emptyMap(),
        val cumulative: CumulativeProjection = CumulativeProjection(),
    )

    private val effectsSnapshot = recordState.mapLatest { state ->
        val record = state.value ?: return@mapLatest EffectsSnapshot()
        val context = currentCoroutineContext()
        val catalog = substanceRepo.getAllSubstances()
        val presented = record.ingestions.sortedWith(compareBy<IngestionWithCompanionAndCustomUnit> { it.ingestion.time }
            .thenBy { it.ingestion.id }).groupBy { it.ingestion.consumerName }.mapValues { (_, rows) ->
            TimelineModelFactory.projectRecords(rows, catalog, record.definitionGraph, record.companionMap) { context.ensureActive() }
        }
        val totals = projectedCumulativeDoses(presented[null].orEmpty(), record.definitionGraph, catalog.associateBy { it.name }) { context.ensureActive() }
        EffectsSnapshot(presented, totals)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), EffectsSnapshot())

    private val myPresentedRecordsFlow = effectsSnapshot.map { it.byConsumer[null].orEmpty() }
    val ingestionElementsFlow = myPresentedRecordsFlow.map { records -> records.map { it.element } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val dataForEffectTimelinesFlow = myPresentedRecordsFlow.map { records -> records.flatMap { it.curves } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val cumulativeProjectionFlow = effectsSnapshot.map { it.cumulative }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), CumulativeProjection())
    val cumulativeDosesFlow = cumulativeProjectionFlow.map { it.totals }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

    val consumersWithIngestionsFlow = combine(effectsSnapshot, isTimelineHiddenFlow, areSubstanceHeightsIndependentFlow) {
            snapshot, isHidden, independent ->
        snapshot.byConsumer.mapNotNull { (consumer, records) ->
            if (consumer == null) return@mapNotNull null
            val lines = records.flatMap { it.curves }
            val display = when {
                isHidden -> TimelineDisplayOption.Hidden
                lines.none { it.roaDuration != null } -> TimelineDisplayOption.NotWorthDrawing
                else -> TimelineDisplayOption.Shown(AllTimelinesModel(lines, emptyList(), emptyList(), independent))
            }
            ConsumerWithIngestions(consumer, records.map { it.element }, lines, display)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

    val interactionsFlow =
        myPresentedRecordsFlow.combine(combinationSettingsStorage.enabledInteractionsFlow) { records, enabledInteractions ->
            val interactionsToCheck =
                records.flatMap { it.contributions }.mapNotNull { (it.subject as? com.isaakhanimann.journal.data.definitions.SubjectId.Catalog)?.name }.plus(enabledInteractions).distinct()
            return@combine interactionsToCheck.flatMapIndexed { index: Int, interaction: String ->
                return@flatMapIndexed interactionsToCheck.drop(index + 1).mapNotNull { other ->
                    interactionChecker.getInteractionBetween(
                        interaction,
                        other
                    )
                }
            }.sortedByDescending { it.interactionType.dangerCount }
        }
            .flowOn(Dispatchers.IO) // if this wasn't on the background the navigation from journal screen to this screen would jump
            .stateIn(
                initialValue = emptyList(),
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(0)
            )

    val interactionExplanationsFlow = interactionsFlow.map { interactions ->
        interactions.flatMap {
            listOf(it.aName, it.bName)
        }.distinct().mapNotNull {
            val substance = substanceRepo.getSubstance(substanceName = it) ?: return@mapNotNull null
            return@mapNotNull InteractionExplanation(
                name = substance.name,
                url = substance.interactionExplanationURL
            )
        }
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0)
    )

    fun deleteExperience(onSuccess: () -> Unit) = session.run(onSuccess) {
        experienceRepo.deleteEverythingOfExperience(experienceId, session.generation)
    }

    fun saveLastIngestionTimeOfExperience(onSuccess: () -> Unit) = session.run(onSuccess) {
        sessionGuard.withSession(session.generation) {
        check(experienceRepo.getExperience(experienceId) != null) { "This experience was deleted." }
        val lastIngestionTime = experienceRepo.getIngestionsWithCompanions(experienceId).maxOfOrNull { it.ingestion.time }
        userPreferences.saveIngestionTimes(lastIngestionTime, null)
        }
    }

    val timelineDisplayOptionFlow = combine(
        dataForEffectTimelinesFlow,
        isTimelineHiddenFlow,
        ratingsFlow,
        timedNotesSortedFlow,
        areSubstanceHeightsIndependentFlow
    ) { lines, hidden, ratings, notes, independent ->
        com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.buildTimelineDisplay(
            lines, ratings, notes, independent, hidden)
    }.flowOn(Dispatchers.Default)
        .stateIn(
            initialValue = TimelineDisplayOption.Loading,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0)
        )

    companion object {

        fun getCumulativeDoses(ingestions: List<IngestionWithAssociatedData>): List<CumulativeDose> {
            return ingestions.groupBy { it.ingestionWithCompanionAndCustomUnit.displayName }
                .map { groupedBySubstanceName ->
                    val ingestionsOfSameSubstance = groupedBySubstanceName.value
                    val cumulativeRouteDose =
                        ingestionsOfSameSubstance.groupBy { it.ingestionWithCompanionAndCustomUnit.ingestion.administrationRoute }
                            .mapNotNull { groupedByRoute ->
                                val groupedIngestions = groupedByRoute.value
                                if (groupedIngestions.any { it.ingestionWithCompanionAndCustomUnit.pureDose == null }) return@mapNotNull null
                                val firstIngestion =
                                    groupedIngestions.first().ingestionWithCompanionAndCustomUnit
                                val units = firstIngestion.originalUnit ?: return@mapNotNull null
                                if (groupedIngestions.any { it.ingestionWithCompanionAndCustomUnit.originalUnit != units }) return@mapNotNull null
                                val isEstimate =
                                    groupedIngestions.any { it.ingestionWithCompanionAndCustomUnit.ingestion.isDoseAnEstimate || it.ingestionWithCompanionAndCustomUnit.customUnit?.isEstimate ?: false }
                                val cumulativeDose =
                                    groupedIngestions.mapNotNull { it.ingestionWithCompanionAndCustomUnit.pureDose }
                                        .sum()
                                val cumulativeDoseStandardDeviation =
                                    groupedIngestions.mapNotNull { it.ingestionWithCompanionAndCustomUnit.pureDoseStandardDeviation }
                                        .sum()
                                val numDots = groupedIngestions.first().roaDose?.getNumDots(
                                    ingestionDose = cumulativeDose,
                                    ingestionUnits = units
                                )
                                CumulativeRouteAndDose(
                                    cumulativeDose = cumulativeDose,
                                    units = units,
                                    isEstimate = isEstimate,
                                    cumulativeDoseStandardDeviation = if (cumulativeDoseStandardDeviation > 0) cumulativeDoseStandardDeviation else null,
                                    numDots = numDots,
                                    route = firstIngestion.ingestion.administrationRoute,
                                    hasMoreThanOneIngestion = groupedIngestions.size > 1
                                )
                            }
                    return@map CumulativeDose(
                        substanceName = groupedBySubstanceName.key,
                        cumulativeRouteAndDose = cumulativeRouteDose
                    )
                }
                .filter { cumulativeDose ->
                    cumulativeDose.cumulativeRouteAndDose.isNotEmpty() && cumulativeDose.cumulativeRouteAndDose.any { it.hasMoreThanOneIngestion }
                }
        }
    }
}

inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> {
    return combine(
        flow,
        flow2,
        flow3,
        flow4,
        flow5,
        flow6
    ) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
        )
    }
}

sealed class TimelineDisplayOption {
    data object Loading : TimelineDisplayOption()
    data object Hidden : TimelineDisplayOption()
    data object NotWorthDrawing : TimelineDisplayOption()
    data class Shown(val allTimelinesModel: AllTimelinesModel) : TimelineDisplayOption()
}

data class IngestionWithAssociatedData(
    val ingestionWithCompanionAndCustomUnit: IngestionWithCompanionAndCustomUnit,
    val roaDuration: RoaDuration?,
    val roaDose: RoaDose?
)
