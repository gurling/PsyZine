// PsyZine modifications, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.data.active

import androidx.room.withTransaction
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.classes.roa.IngestionPhase
import com.isaakhanimann.journal.data.substances.classes.roa.calculateCurrentPhase
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.IngestionElement
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.TimelineModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveIngestion(val substanceName: String, val dose: String, val route: String, val phase: IngestionPhase, val color: AdaptiveColor)
data class ActiveExperience(val id: Int, val generation: String, val title: String, val ingestions: List<ActiveIngestion>,
    val timeline: AllTimelinesModel?, val firstIngestionTime: Instant, val updatedAt: Instant)

@Singleton
class ActiveExperienceRepository @Inject constructor(private val db: AppDatabase, private val substances: SubstanceRepository, private val clock: Clock) {
    // Observe invalidations without loading the entire private journal for every update.
    val changes = db.experienceDao().observeActiveChanges(SimpleSQLiteQuery("SELECT 1")).map { Unit }

    suspend fun current(): ActiveExperience? = withContext(Dispatchers.Default) {
        substances.initialize()
        val now = clock.instant()
        val (relation, generation) = db.withTransaction {
            val generation = db.photoAttachmentDao().generation() ?: java.util.UUID.randomUUID().toString().also {
                db.photoAttachmentDao().setGeneration(com.isaakhanimann.journal.data.room.attachments.JournalState(generation = it))
            }
            db.experienceDao().getMostRecentActiveExperience(now.minus(12, ChronoUnit.HOURS), now) to generation
        }
        val experience = relation ?: return@withContext null
        val ingestions = experience.ingestionsWithCompanions.sortedBy { it.ingestion.time }
        if (ingestions.isEmpty()) return@withContext null
        val sourceSubstances = ingestions.mapNotNull { substances.getSubstance(it.ingestion.substanceName) }.distinctBy { it.name }
        val elements = ingestions.map { iwc ->
            val roa = sourceSubstances.firstOrNull { it.name == iwc.ingestion.substanceName }?.getRoa(iwc.ingestion.administrationRoute)
            IngestionElement(iwc, roa?.roaDuration, roa?.roaDose?.getNumDots(iwc.pureDose, iwc.originalUnit))
        }
        val display = elements.map { element ->
            val iwc = element.ingestionWithCompanionAndCustomUnit
            ActiveIngestion(iwc.ingestion.substanceName, iwc.doseDescription, iwc.ingestion.administrationRoute.displayText.lowercase(),
                calculateCurrentPhase(iwc.ingestion.time, element.roaDuration, now), iwc.substanceCompanion?.color ?: AdaptiveColor.RED)
        }
        val lines = TimelineModelFactory.effectLines(elements, sourceSubstances)
        val model = if (lines.isNotEmpty() && elements.any { it.roaDuration != null })
            AllTimelinesModel(lines, emptyList(), emptyList(), true) else null
        ActiveExperience(experience.experience.id, generation, experience.experience.title, display, model, ingestions.first().ingestion.time, now)
    }
}
