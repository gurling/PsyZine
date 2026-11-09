// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import androidx.sqlite.db.SimpleSQLiteQuery
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import javax.inject.Inject
import javax.inject.Singleton

data class SubjectBrowserSnapshot(val tags: TagFilterSnapshot, val definitions: List<DefinitionWithContent>,
    val locals: List<CustomSubstance>, val recentNames: List<String>,
    val journal: List<ExperienceWithIngestionsTimedNotesAndRatings> = emptyList())

/** One generation-bound read prevents restored IDs from mixing with old search results. */
@Singleton
class SubjectBrowserRepository @Inject constructor(private val db: AppDatabase, private val guard: JournalSessionGuard) {
    val snapshots get() = observe(includeJournal = false)
    fun observe(includeJournal: Boolean) = (if (includeJournal)
        db.definitionDao().observeJournalSearchChanges(SimpleSQLiteQuery("SELECT 1"))
        else db.definitionDao().observeSubjectBrowserChanges(SimpleSQLiteQuery("SELECT 1")))
        .map {
            val snapshot = guard.readSession(null) { generation ->
                val dao = db.definitionDao()
                val definitions = dao.definitions()
                val assignmentRows = dao.memberships()
                val memberships = assignmentRows.groupBy { it.subjectId }
                    .mapValues { (_, rows) -> rows.map { it.tagId }.toSet() }
                val tags = TagFilterSnapshot(generation, dao.tags(), memberships,
                    assignmentRows.filter { it.isPrimary }.associate { it.subjectId to it.tagId })
                BrowserRead(tags, definitions, db.experienceDao().getAllCustomSubstances(), dao.recentBrowserIngestions(),
                    if (includeJournal) db.experienceDao().getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted() else emptyList())
            }
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                val index = DefinitionSearchIndex(snapshot.definitions)
                val names = snapshot.ingestions.flatMap { record ->
                    record.customUnitId?.let(index::leafNames)?.toList()
                        ?: listOfNotNull(record.substanceName)
                }.distinct()
                SubjectBrowserSnapshot(snapshot.tags, snapshot.definitions.sortedWith(
                    compareByDescending<DefinitionWithContent> { it.unit.creationDate }.thenByDescending { it.unit.id }),
                    snapshot.locals, names, snapshot.journal)
            }
        }.flowOn(Dispatchers.IO)
}

private data class BrowserRead(val tags: TagFilterSnapshot, val definitions: List<DefinitionWithContent>,
    val locals: List<CustomSubstance>, val ingestions: List<com.isaakhanimann.journal.data.room.experiences.entities.Ingestion>,
    val journal: List<ExperienceWithIngestionsTimedNotesAndRatings>)
