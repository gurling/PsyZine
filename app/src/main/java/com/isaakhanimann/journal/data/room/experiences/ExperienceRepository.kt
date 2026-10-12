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

package com.isaakhanimann.journal.data.room.experiences

import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.experiences.entities.TimedNote
import com.isaakhanimann.journal.data.room.experiences.relations.CustomUnitWithIngestions
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestions
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanion
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExperienceRepository @Inject constructor(private val experienceDao: ExperienceDao,
    private val photoDao: com.isaakhanimann.journal.data.room.attachments.PhotoAttachmentDao,
    private val storage: com.isaakhanimann.journal.data.attachments.AttachmentStorage,
    private val lock: com.isaakhanimann.journal.data.attachments.JournalMutationLock,
    private val sessionGuard: com.isaakhanimann.journal.data.room.JournalSessionGuard) {
    private suspend fun requireParent(id: Int) {
        check(experienceDao.getExperience(id) != null) { "This experience was deleted. Close this screen." }
    }

    suspend fun insert(rating: ShulginRating, generation: String?) = sessionGuard.withSession(generation, rating.experienceId) {
        check(rating.time != null || experienceDao.getRatingsFlow(rating.experienceId).first().none { it.time == null }) {
            "This experience already has an overall rating."
        }
        experienceDao.insert(rating)
    }

    suspend fun insert(note: TimedNote, generation: String?) = sessionGuard.withSession(generation, note.experienceId) {
        experienceDao.insert(note)
    }

    suspend fun update(rating: ShulginRating, generation: String?) = sessionGuard.withSession(generation, rating.experienceId) {
        check(experienceDao.getRating(rating.id)?.experienceId == rating.experienceId) { "This rating was deleted." }
        check(rating.time != null || experienceDao.getRatingsFlow(rating.experienceId).first().none { it.id != rating.id && it.time == null }) { "This experience already has an overall rating." }
        check(experienceDao.update(rating) == 1) { "This rating was deleted." }
    }

    suspend fun update(note: TimedNote, generation: String?) = sessionGuard.withSession(generation, note.experienceId) {
        check(experienceDao.getTimedNote(note.id)?.experienceId == note.experienceId) { "This note was deleted." }
        check(experienceDao.update(note) == 1) { "This note was deleted." }
    }

    suspend fun update(ingestion: Ingestion, previousParent: Int, generation: String?) = sessionGuard.withSession(generation, previousParent) {
        requireParent(ingestion.experienceId)
        check(experienceDao.getIngestion(ingestion.id)?.experienceId == previousParent) { "This ingestion was deleted or moved. Reopen it." }
        ingestion.customUnitId?.let { id ->
            val unit = experienceDao.getCustomUnit(id)
            check(unit != null && unit.substanceName == ingestion.substanceName && unit.administrationRoute == ingestion.administrationRoute) { "This custom unit was deleted or changed. Choose a unit again." }
            com.isaakhanimann.journal.data.room.validateCustomUnitDose(ingestion.dose, ingestion.estimatedDoseStandardDeviation, ingestion.isDoseAnEstimate, unit)
        }
        com.isaakhanimann.journal.data.room.validateDose(ingestion.dose, ingestion.estimatedDoseStandardDeviation)
        require(ingestion.endTime == null || ingestion.endTime!! >= ingestion.time) { "End time must not be before start time." }
        check(experienceDao.update(ingestion) == 1) { "This ingestion was deleted." }
    }

    suspend fun delete(rating: ShulginRating, generation: String?) = sessionGuard.withSession(generation, rating.experienceId) {
        check(experienceDao.getRating(rating.id)?.experienceId == rating.experienceId && experienceDao.delete(rating) == 1) { "This rating was deleted." }
    }

    suspend fun delete(note: TimedNote, generation: String?) = sessionGuard.withSession(generation, note.experienceId) {
        check(experienceDao.getTimedNote(note.id)?.experienceId == note.experienceId && experienceDao.delete(note) == 1) { "This note was deleted." }
    }

    suspend fun delete(ingestion: Ingestion, generation: String?) = sessionGuard.withSession(generation, ingestion.experienceId) {
        check(experienceDao.getIngestion(ingestion.id)?.experienceId == ingestion.experienceId && experienceDao.delete(ingestion) == 1) { "This ingestion was deleted or moved." }
    }

    suspend fun updateFavorite(id: Int, value: Boolean, generation: String?) = sessionGuard.withSession(generation, id) {
        check(experienceDao.updateFavorite(id, value) == 1) { "This experience was deleted." }
    }

    suspend fun deleteEverythingOfExperience(id: Int, generation: String?) = sessionGuard.withSession(generation, id) {
        val photos = photoDao.forExperience(id)
        experienceDao.deleteEverythingOfExperience(id)
        withContext(Dispatchers.IO) { collectLater(photos) }
    }

    fun observeCalendarIngestions(from: Instant, to: Instant) = experienceDao.observeCalendarIngestions(from, to)

    private fun collectLater(photos: List<com.isaakhanimann.journal.data.room.attachments.PhotoAttachment>) {
        photos.forEach { runCatching { storage.file(it.relativePath).setLastModified(System.currentTimeMillis()) } }
    }

    suspend fun insert(customUnit: CustomUnit) = experienceDao.insert(customUnit).toInt()
    suspend fun update(customUnit: CustomUnit) { check(experienceDao.update(customUnit) == 1) { "This record was deleted. Close this screen." } }

    suspend fun migrateBenzydamine() = experienceDao.migrateBenzydamine()
    suspend fun migrateCannabisAndMushroomUnits() = experienceDao.migrateCannabisAndMushroomUnits()
    suspend fun insertIngestionExperienceAndCompanion(
        ingestion: Ingestion,
        experience: Experience,
        substanceCompanion: SubstanceCompanion
    ) = experienceDao.insertIngestionExperienceAndCompanion(
        ingestion,
        experience,
        substanceCompanion
    )

    suspend fun insertEverything(
        journalExport: JournalExport
    ) = experienceDao.insertEverything(journalExport)

    suspend fun insertIngestionAndCompanion(
        ingestion: Ingestion,
        substanceCompanion: SubstanceCompanion
    ) = experienceDao.insertIngestionAndCompanion(
        ingestion,
        substanceCompanion
    )

    suspend fun deleteEverything() = lock.mutex.withLock {
        val photos = photoDao.all()
        withContext(NonCancellable) {
            experienceDao.deleteEverythingAndInvalidate(java.util.UUID.randomUUID().toString())
            collectLater(photos)
        }
    }

    suspend fun delete(customUnit: CustomUnit) { check(experienceDao.delete(customUnit) == 1) { "This record was deleted. Close this screen." } }

    suspend fun deleteUnusedSubstanceCompanions() =
        experienceDao.deleteUnusedSubstanceCompanions()

    suspend fun getSortedExperiencesWithIngestionsWithSortDateBetween(
        fromInstant: Instant,
        toInstant: Instant
    ): List<ExperienceWithIngestions> =
        experienceDao.getSortedExperiencesWithIngestionsWithSortDateBetween(fromInstant, toInstant)

    fun getSortedExperienceWithIngestionsCompanionsAndRatingsFlow(): Flow<List<ExperienceWithIngestionsCompanionsAndRatings>> =
        experienceDao.getSortedExperienceWithIngestionsCompanionsAndRatingsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedExperiencesWithIngestionsFlow(): Flow<List<ExperienceWithIngestions>> =
        experienceDao.getSortedExperiencesWithIngestionsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedExperiencesWithIngestionsAndCustomUnitsFlow(): Flow<List<ExperienceWithIngestionsAndCompanions>> =
        experienceDao.getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getCustomSubstancesFlow(): Flow<List<CustomSubstance>> =
        experienceDao.getCustomSubstancesFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getCustomSubstanceFlow(id: Int): Flow<CustomSubstance?> =
        experienceDao.getCustomSubstanceFlow(id)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getCustomSubstance(name: String): CustomSubstance? =
        experienceDao.getCustomSubstance(name)

    suspend fun getCustomSubstance(id: Int): CustomSubstance? = experienceDao.getCustomSubstance(id)

    fun getIngestionsWithExperiencesFlow(
        fromInstant: Instant,
        toInstant: Instant
    ): Flow<List<IngestionWithExperienceAndCustomUnit>> =
        experienceDao.getIngestionWithExperiencesFlow(fromInstant, toInstant)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getIngestionsWithCompanions(
        fromInstant: Instant,
        toInstant: Instant
    ): List<IngestionWithCompanion> =
        experienceDao.getIngestionsWithCompanions(fromInstant, toInstant)

    fun getSortedLastUsedSubstanceNamesFlow(limit: Int): Flow<List<String>> =
        experienceDao.getSortedLastUsedSubstanceNamesFlow(limit).flowOn(Dispatchers.IO).conflate()

    suspend fun getExperience(id: Int): Experience? = experienceDao.getExperience(id)
    suspend fun getExperienceWithIngestionsCompanionsAndRatings(id: Int): ExperienceWithIngestionsCompanionsAndRatings? =
        experienceDao.getExperienceWithIngestionsCompanionsAndRatings(id)

    fun observeExperienceWithIngestionsCompanionsAndRatings(id: Int) =
        experienceDao.observeExperienceWithIngestionsCompanionsAndRatings(id).flowOn(Dispatchers.IO).conflate()

    suspend fun getIngestionsWithCompanions(experienceId: Int) =
        experienceDao.getIngestionsWithCompanions(experienceId)

    suspend fun getIngestion(id: Int): Ingestion? = experienceDao.getIngestion(id)

    suspend fun getRating(id: Int): ShulginRating? = experienceDao.getRating(id)
    suspend fun getTimedNote(id: Int): TimedNote? = experienceDao.getTimedNote(id)
    suspend fun getCustomUnit(id: Int): CustomUnit? = experienceDao.getCustomUnit(id)
    suspend fun getCustomUnitWithIngestions(id: Int): CustomUnitWithIngestions? = experienceDao.getCustomUnitWithIngestions(id)
    fun getIngestionFlow(id: Int) = experienceDao.getIngestionFlow(id)
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getIngestionsWithCompanionsFlow(experienceId: Int) =
        experienceDao.getIngestionsWithCompanionsFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getRatingsFlow(experienceId: Int) =
        experienceDao.getRatingsFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getTimedNotesFlowSorted(experienceId: Int) =
        experienceDao.getTimedNotesFlowSorted(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getExperienceFlow(experienceId: Int) =
        experienceDao.getExperienceFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getLatestIngestionOfEverySubstanceSinceDate(instant: Instant): List<Ingestion> =
        experienceDao.getLatestIngestionOfEverySubstanceSinceDate(instant)

    suspend fun getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted(): List<ExperienceWithIngestionsTimedNotesAndRatings> =
        experienceDao.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()

    suspend fun getAllCustomUnitsSorted(): List<CustomUnit> =
        experienceDao.getAllCustomUnitsSorted()

    suspend fun getAllCustomSubstances(): List<CustomSubstance> =
        experienceDao.getAllCustomSubstances()

    suspend fun getAllSubstanceCompanions(): List<SubstanceCompanion> =
        experienceDao.getAllSubstanceCompanions()

    suspend fun getTimedNotes(experienceId: Int): List<TimedNote> =
        experienceDao.getTimedNotes(experienceId)

    suspend fun delete(substanceCompanion: SubstanceCompanion) =
        experienceDao.delete(substanceCompanion)

    suspend fun update(substanceCompanion: SubstanceCompanion) {
        check(experienceDao.update(substanceCompanion) == 1) { "This substance color was deleted." }
    }

    suspend fun insert(customSubstance: CustomSubstance): Int =
        experienceDao.insert(customSubstance).toInt()

    suspend fun delete(customSubstance: CustomSubstance) {
        check(experienceDao.delete(customSubstance) == 1) { "This substance was deleted. Close this screen." }
    }

    suspend fun update(customSubstance: CustomSubstance) {
        check(experienceDao.update(customSubstance) == 1) { "This substance was deleted. Close this screen." }
    }

    fun getSortedIngestionsWithSubstanceCompanionsFlow(limit: Int) =
        experienceDao.getSortedIngestionsWithSubstanceCompanionsFlow(limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedIngestions(limit: Int) =
        experienceDao.getSortedIngestions(limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedIngestionsFlow(substanceName: String, limit: Int) =
        experienceDao.getSortedIngestionsFlow(substanceName, limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName: String) =
        experienceDao.getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getAllSubstanceCompanionsFlow() = experienceDao.getAllSubstanceCompanionsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getCustomUnitsFlow(isArchived: Boolean) = experienceDao.getSortedCustomUnitsFlow(isArchived)
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getUnArchivedCustomUnitsFlow(substanceName: String) =
        experienceDao.getSortedCustomUnitsFlowBasedOnName(substanceName, false)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getAllCustomUnitsFlow() = experienceDao.getAllCustomUnitsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getSubstanceCompanionFlow(substanceName: String) =
        experienceDao.getSubstanceCompanionFlow(substanceName)
            .flowOn(Dispatchers.IO)
            .conflate()
}
