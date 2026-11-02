// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class SubjectBrowserRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SubjectBrowserRepository
    private fun unit(name: String) = CustomUnit(1, null, name, Instant.EPOCH, null, null, null, false, true,
        "capsule", null, null, "", isHiddenInTimeline = true)
    private fun ingestion(id: Int, name: String?, time: Long, definition: Int? = null) = Ingestion(id = id,
        substanceName = name, time = Instant.ofEpochSecond(time), administrationRoute = AdministrationRoute.ORAL,
        dose = null, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = null,
        experienceId = 1, notes = null, stomachFullness = null, consumerName = "Other", customUnitId = definition)
    @Before fun setup(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = SubjectBrowserRepository(db, JournalSessionGuard(db, JournalMutationLock()))
        db.photoAttachmentDao().setGeneration(JournalState(generation = "g0"))
    }
    @After fun cleanup() { db.close() }
    @Test fun recentsLimitStoredIngestionsBeforeHiddenCompositionExpansion() = runBlocking {
        db.withTransaction {
            db.experienceDao().insert(unit("Archived blend"))
            db.definitionDao().insertComponents(listOf(DoseComponent(1, 1, substanceName = "B", dose = Double.NaN, isHiddenInTimeline = true),
                DoseComponent(2, 1, substanceName = "A")))
            db.experienceDao().insert(ingestion(1, "Outside window", 1))
            for (id in 2..501) db.experienceDao().insert(ingestion(id, "A", id.toLong()))
            db.experienceDao().insert(ingestion(502, null, 502, 1))
        }
        val result = withTimeout(15000) { repo.snapshots.first() }
        assertEquals(listOf("B", "A"), result.recentNames)
        assertTrue(result.definitions.single().unit.isArchived)
        assertTrue(result.definitions.single().unit.isHiddenInTimeline)
    }
    @Test fun restoresAndMembershipEditsRefreshOneCoherentSnapshot() = runBlocking {
        val tagId = "00000000-0000-0000-0000-000000000001"
        db.experienceDao().insert(unit("g0"))
        val received = Channel<SubjectBrowserSnapshot>(Channel.UNLIMITED)
        val job = launch { repo.snapshots.collect { received.send(it) } }
        suspend fun next(test: (SubjectBrowserSnapshot) -> Boolean) = withTimeout(15000) {
            var value = received.receive()
            while (!test(value)) value = received.receive()
            value
        }
        try {
            next { it.tags.generation == "g0" }
            db.withTransaction {
                db.photoAttachmentDao().setGeneration(JournalState(generation = "g1"))
                db.experienceDao().update(unit("g1"))
                db.definitionDao().saveTag(SubstanceTag(tagId, "g1", "BLUE"))
                db.definitionDao().insertMemberships(listOf(SubstanceTagMembership("custom:1", tagId, true)))
            }
            val changed = next { it.tags.generation == "g1" }
            assertEquals("g1", changed.definitions.single().unit.name)
            assertEquals("g1", changed.tags.tags.single().name)
            assertEquals(setOf(tagId), changed.tags.memberships["custom:1"])
            db.definitionDao().deleteMemberships("custom:1")
            next { it.tags.memberships.isEmpty() }
            db.experienceDao().deleteEverythingAndInvalidate("g2")
            val restored = next { it.tags.generation == "g2" }
            assertTrue(restored.definitions.isEmpty()); assertTrue(restored.tags.tags.isEmpty()); assertTrue(restored.recentNames.isEmpty())
        } finally { job.cancelAndJoin(); received.close() }
    }
    @Test fun journalSnapshotsReactToNotesFavoritesAndAtomicGenerationChanges() = runBlocking {
        db.experienceDao().insert(Experience(1, "g0", "", Instant.EPOCH, Instant.EPOCH, false, null))
        val received = Channel<SubjectBrowserSnapshot>(Channel.UNLIMITED)
        val job = launch { repo.observe(includeJournal = true).collect { received.send(it) } }
        suspend fun next(test: (SubjectBrowserSnapshot) -> Boolean) = withTimeout(15000) {
            var value = received.receive()
            while (!test(value)) value = received.receive()
            value
        }
        try {
            next { it.journal.size == 1 }
            db.experienceDao().insert(TimedNote(1, Instant.EPOCH, Instant.EPOCH, "Reactive note", AdaptiveColor.BLUE, 1, false))
            assertEquals("Reactive note", next { it.journal.single().timedNotes.isNotEmpty() }.journal.single().timedNotes.single().note)
            db.experienceDao().updateFavorite(1, true)
            next { it.journal.single().experience.isFavorite }
            db.withTransaction {
                db.photoAttachmentDao().setGeneration(JournalState(generation = "g1"))
                delay(10)
                db.experienceDao().update(Experience(1, "g1", "", Instant.EPOCH, Instant.EPOCH, false, null))
            }
            val replaced = next { it.tags.generation == "g1" }
            assertEquals("g1", replaced.journal.single().experience.title)
        } finally { job.cancelAndJoin(); received.close() }
    }
}
