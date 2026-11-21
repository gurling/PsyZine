// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.experiences

import androidx.sqlite.db.SimpleSQLiteQuery
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.TimedNote
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import com.isaakhanimann.journal.data.definitions.DefinitionWithContent
import com.isaakhanimann.journal.data.definitions.toGraph
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class JournalReadSnapshot<T>(val generation: String, val value: T)
data class JournalTimelineRecord(
    val ingestions: List<IngestionWithCompanionAndCustomUnit>,
    val ratings: List<ShulginRating>,
    val timedNotes: List<TimedNote>,
    val experience: Experience? = null,
    val photos: List<PhotoAttachment> = emptyList(),
    val definitions: List<DefinitionWithContent> = emptyList(),
    val companions: List<SubstanceCompanion> = emptyList(),
) {
    val definitionGraph by lazy { definitions.toGraph() }
    val companionMap by lazy { companions.associateBy { it.substanceName } }
}
class MissingJournalRecord : IllegalStateException("This experience was deleted. Go back and choose another record.")

/** Read generation and content under one guarded transaction for every invalidation. */
@Singleton
class JournalRecordRepository @Inject constructor(
    private val database: AppDatabase,
    private val guard: JournalSessionGuard,
) {
    fun observePhotos(id: Int, generation: String?) = observe(id, generation) {
        database.photoAttachmentDao().forExperience(id)
    }

    fun observeTimeline(id: Int, generation: String?) = observe(id, generation) {
        val relation = database.experienceDao().getExperienceWithIngestionsCompanionsAndRatings(id)
            ?: throw MissingJournalRecord()
        JournalTimelineRecord(relation.ingestionsWithCompanions, relation.ratings,
            database.experienceDao().getTimedNotes(id).sortedWith(compareBy<TimedNote> { it.time }.thenBy { it.id }),
            relation.experience, database.photoAttachmentDao().forExperience(id),
            database.experienceDao().definitionContents(), database.experienceDao().getAllSubstanceCompanions())
    }

    private fun <T> observe(id: Int, recoveredGeneration: String?, read: suspend () -> T): Flow<JournalReadSnapshot<T>> = flow {
        var boundGeneration = recoveredGeneration
        database.experienceDao().observeRecordChanges(SimpleSQLiteQuery("SELECT 1")).collect {
            val snapshot = guard.readSession(boundGeneration) { current ->
                if (database.experienceDao().getExperience(id) == null) throw MissingJournalRecord()
                JournalReadSnapshot(current, read())
            }
            boundGeneration = snapshot.generation
            emit(snapshot)
        }
    }.flowOn(Dispatchers.IO)
}
