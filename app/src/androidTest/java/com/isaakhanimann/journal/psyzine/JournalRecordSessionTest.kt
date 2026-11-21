// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import com.isaakhanimann.journal.data.room.experiences.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.utils.journalReadSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class JournalRecordSessionTest {
    private lateinit var db: AppDatabase
    private lateinit var guard: JournalSessionGuard
    private lateinit var records: JournalRecordRepository
    private lateinit var generation: String
    private val entry = Experience(42, "Original", "", sortDate = Instant.EPOCH, location = null)
    private val ingestion = Ingestion(1, "Caffeine", Instant.EPOCH, administrationRoute = AdministrationRoute.ORAL,
        dose = 100.0, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg",
        experienceId = 42, notes = "Original", stomachFullness = null, consumerName = null, customUnitId = null)
    private fun photo(position: Int) = UUID.randomUUID().toString().let {
        PhotoAttachment(it, 42, "attachments/$it.png", "image/png", 1, 1, 1, Instant.EPOCH, position, "0".repeat(64))
    }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        guard = JournalSessionGuard(db, JournalMutationLock())
        records = JournalRecordRepository(db, guard)
        generation = guard.currentGeneration()
        db.experienceDao().insert(entry)
        db.experienceDao().insert(ingestion)
    }
    @After fun cleanup() { db.close() }

    private suspend fun replace() = db.withTransaction {
        db.experienceDao().deleteEverythingAndInvalidate("replacement")
        db.experienceDao().insert(entry.copy(title = "Replacement"))
        db.experienceDao().insert(ingestion.copy(notes = "Replacement"))
        db.photoAttachmentDao().insert(listOf(photo(0)))
    }

    @Test fun staleRoutesRejectReusedIdsBeforeLoadingAnyContent() = runBlocking<Unit> {
        replace()
        try { records.observePhotos(42, generation).first(); fail("Stale photos loaded") }
        catch (_: StaleJournalSession) { }
        try { records.observeTimeline(42, generation).first(); fail("Stale timeline loaded") }
        catch (_: StaleJournalSession) { }
        assertEquals("Replacement", db.experienceDao().getExperience(42)!!.title)
    }

    @Test fun openStreamsRejectRestoresWithoutEmittingReplacementContent() = runBlocking<Unit> {
        val photosReady = CompletableDeferred<Unit>()
        val timelineReady = CompletableDeferred<Unit>()
        val photosError = CompletableDeferred<Throwable>()
        val timelineError = CompletableDeferred<Throwable>()
        val photos = launch {
            records.observePhotos(42, generation).onEach {
                assertEquals(generation, it.generation)
                assertTrue(it.value.isEmpty())
                photosReady.complete(Unit)
            }.catch { photosError.complete(it) }.collect()
        }
        val timeline = launch {
            records.observeTimeline(42, generation).onEach {
                assertEquals("Original", it.value.ingestions.single().ingestion.notes)
                timelineReady.complete(Unit)
            }.catch { timelineError.complete(it) }.collect()
        }
        try {
            withTimeout(10000) { photosReady.await(); timelineReady.await() }
            replace()
            assertTrue(withTimeout(10000) { photosError.await() } is StaleJournalSession)
            assertTrue(withTimeout(10000) { timelineError.await() } is StaleJournalSession)
        } finally { photos.cancelAndJoin(); timeline.cancelAndJoin() }
    }

    @Test fun deletingAnEntryInvalidatesEvenAnEmptyPhotoViewer() = runBlocking<Unit> {
        val ready = CompletableDeferred<Unit>()
        val error = CompletableDeferred<Throwable>()
        val watch = launch {
            records.observePhotos(42, generation).onEach { ready.complete(Unit) }
                .catch { error.complete(it) }.collect()
        }
        try {
            withTimeout(10000) { ready.await() }
            db.experienceDao().deleteEverythingOfExperience(42)
            assertTrue(withTimeout(10000) { error.await() } is MissingJournalRecord)
        } finally { watch.cancelAndJoin() }
    }

    @Test fun photoOrderAndTimelineChildrenComeFromCoherentTransactions() = runBlocking<Unit> {
        val later = photo(2)
        val earlier = photo(1)
        db.photoAttachmentDao().insert(listOf(later, earlier))
        assertEquals(listOf(earlier.id, later.id), records.observePhotos(42, generation).first().value.map { it.id })
        val values = Channel<JournalReadSnapshot<JournalTimelineRecord>>(Channel.UNLIMITED)
        val watch = launch { records.observeTimeline(42, generation).collect { values.send(it) } }
        try {
            withTimeout(10000) { values.receive() }
            db.withTransaction {
                db.experienceDao().update(ingestion.copy(notes = "Edited"))
                db.experienceDao().insert(TimedNote(id = 1, time = Instant.EPOCH, note = "Committed together",
                    color = AdaptiveColor.RED, experienceId = 42, isPartOfTimeline = true))
            }
            val updated = withTimeout(10000) {
                var next = values.receive()
                while (next.value.ingestions.single().ingestion.notes != "Edited") next = values.receive()
                next.value
            }
            assertEquals("Committed together", updated.timedNotes.single().note)
        } finally { watch.cancelAndJoin(); values.close() }
    }

    @Test fun resumeKeepsSavedGenerationAndReportsRestoredDataInsteadOfRebinding() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = SavedStateHandle()
        val session = journalReadSession(scope, state) { records.observePhotos(42, it) }
        val firstReady = CompletableDeferred<Unit>()
        var watch = scope.launch { session.collect { if (it.value != null) firstReady.complete(Unit) } }
        try {
            withTimeout(10000) { firstReady.await() }
            watch.cancelAndJoin()
            withTimeout(10000) { session.first { it.loading } }
            assertEquals(generation, state.get<String>("journalGeneration"))
            replace()
            watch = scope.launch { session.collect() }
            val failed = withTimeout(10000) { session.first { it.error != null } }
            assertNull(failed.value)
            assertTrue(failed.error!!.contains("journal changed"))
            assertEquals(generation, state.get<String>("journalGeneration"))
        } finally { watch.cancelAndJoin(); scope.cancel(); scope.coroutineContext[Job]!!.join() }
    }
}
