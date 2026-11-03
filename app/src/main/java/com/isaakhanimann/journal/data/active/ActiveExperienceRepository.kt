// PsyZine modifications, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.data.active

import androidx.room.withTransaction
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.classes.roa.IngestionPhase
import com.isaakhanimann.journal.data.substances.classes.roa.calculateCurrentPhase
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.TimelineModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import com.isaakhanimann.journal.data.room.experiences.delayedTimelineTime

data class ActiveIngestion(val substanceName: String, val dose: String, val route: String, val phase: IngestionPhase, val color: AdaptiveColor)
data class ActiveExperience(val id: Int, val generation: String, val title: String, val ingestions: List<ActiveIngestion>,
    val timeline: AllTimelinesModel?, val firstIngestionTime: Instant, val updatedAt: Instant)

@Singleton
class ActiveExperienceRepository @Inject constructor(private val db: AppDatabase, private val substances: SubstanceRepository, private val clock: Clock) {
    private data class Snapshot(val relation: ExperienceWithIngestionsCompanionsAndRatings?, val generation: String,
        val definitions: List<DefinitionWithContent>, val companions: List<SubstanceCompanion>)
    // Observe invalidations without loading the entire private journal for every update.
    val changes = db.experienceDao().observeActiveChanges(SimpleSQLiteQuery("SELECT 1")).map { Unit }

    suspend fun current(): ActiveExperience? = withContext(Dispatchers.Default) {
        substances.initialize()
        val now = clock.instant()
        val snapshot = db.withTransaction {
            val generation = db.photoAttachmentDao().generation() ?: java.util.UUID.randomUUID().toString().also {
                db.photoAttachmentDao().setGeneration(com.isaakhanimann.journal.data.room.attachments.JournalState(generation = it))
            }
            Snapshot(db.experienceDao().getMostRecentActiveExperience(now.minus(12, ChronoUnit.HOURS), now), generation,
                db.experienceDao().definitionContents(), db.experienceDao().getAllSubstanceCompanions())
        }
        val experience = snapshot.relation ?: return@withContext null
        val graph = snapshot.definitions.toGraph()
        val companions = snapshot.companions.associateBy { it.substanceName }
        val ingestions = experience.ingestionsWithCompanions.sortedBy { it.ingestion.time }
        if (ingestions.isEmpty()) return@withContext null
        val sourceSubstances = substances.getAllSubstances()
        val context = kotlinx.coroutines.currentCoroutineContext()
        val presented = TimelineModelFactory.projectRecords(ingestions, sourceSubstances, graph, companions) { context.ensureActive() }
        val elements = presented.map { it.element }
        val display = elements.map { element ->
            val iwc = element.ingestionWithCompanionAndCustomUnit
            val delayedStart = iwc.ingestion.delayedTimelineTime(iwc.ingestion.time)
            val color = element.displayColor ?: iwc.substanceCompanion?.color ?: AdaptiveColor.RED
            ActiveIngestion(iwc.displayName, iwc.doseDescription, iwc.ingestion.administrationRoute.displayText.lowercase(),
                calculateCurrentPhase(delayedStart ?: iwc.ingestion.time,
                    element.roaDuration.takeIf { delayedStart != null }, now), color)
        }
        val lines = presented.flatMap { it.curves }
        val model = if (lines.isNotEmpty() && lines.any { it.roaDuration != null })
            AllTimelinesModel(lines, emptyList(), emptyList(), true) else null
        ActiveExperience(experience.experience.id, snapshot.generation, experience.experience.title, display, model, ingestions.first().ingestion.time, now)
    }
}
