// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.utils.JournalEditorSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class JournalMutationRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var directory: File
    private lateinit var storage: AttachmentStorage
    private lateinit var guard: JournalSessionGuard
    private lateinit var entries: ExperienceRepository
    private lateinit var attachments: AttachmentRepository
    private val entry = Experience(42,"original","",sortDate = Instant.EPOCH,location = null)
    private val note = TimedNote(1,time = Instant.EPOCH,note = "note",color = AdaptiveColor.RED,experienceId = 42,isPartOfTimeline = true)
    private val rating = ShulginRating(1,time = Instant.EPOCH,option = ShulginRatingOption.PLUS,experienceId = 42)
    private val ingestion = Ingestion(1,"Caffeine",Instant.EPOCH,administrationRoute = AdministrationRoute.ORAL,dose = 0.0001,
        isDoseAnEstimate = true,estimatedDoseStandardDeviation = 1.234567,units = "mg",experienceId = 42,
        notes = "original",stomachFullness = null,consumerName = null,customUnitId = null)
    @Before fun setup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(app.cacheDir,"mutation-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(directory,"files").apply { mkdirs() }
            override fun getCacheDir() = File(directory,"cache").apply { mkdirs() }
        }
        db = Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        storage = AttachmentStorage(context)
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(db,lock)
        entries = ExperienceRepository(db.experienceDao(),db.photoAttachmentDao(),storage,lock,guard)
        attachments = AttachmentRepository(storage,AttachmentDraftStore(storage),ImageProcessor(context,storage),db,lock,context)
        runBlocking { db.experienceDao().insert(entry); db.experienceDao().insert(note); db.experienceDao().insert(rating); db.experienceDao().insert(ingestion) }
    }
    @After fun cleanup() { db.close(); directory.deleteRecursively() }
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Stale mutation must fail") } catch (_: IllegalStateException) { }
    }
    private suspend fun staleActions(generation: String) {
        rejected { entries.insert(note.copy(id=0),generation) }
        rejected { entries.insert(rating.copy(id=0),generation) }
        rejected { entries.update(note.copy(note="stale"),generation) }
        rejected { entries.update(rating.copy(option=ShulginRatingOption.TWO_PLUS),generation) }
        rejected { entries.update(ingestion.copy(notes="stale"),42,generation) }
        rejected { entries.delete(note,generation) }; rejected { entries.delete(rating,generation) }; rejected { entries.delete(ingestion,generation) }
        rejected { entries.updateFavorite(42,true,generation) }; rejected { entries.deleteEverythingOfExperience(42,generation) }
    }
    @Test fun restoredParentAndChildIdsRejectEveryOldMutation() = runBlocking<Unit> {
        val generation = guard.currentGeneration()
        db.withTransaction {
            db.experienceDao().deleteEverythingAndInvalidate("replacement")
            db.experienceDao().insert(entry.copy(title="restored")); db.experienceDao().insert(note)
            db.experienceDao().insert(rating); db.experienceDao().insert(ingestion)
        }
        staleActions(generation)
        assertEquals("restored",entries.getExperience(42)!!.title)
        assertEquals("original",entries.getIngestionFlow(1).first()!!.ingestion.notes)
        assertFalse(entries.getExperience(42)!!.isFavorite)
    }
    @Test fun deletedParentRejectsOldChildrenWithoutChangingGeneration() = runBlocking<Unit> {
        val generation = guard.currentGeneration()
        entries.deleteEverythingOfExperience(42,generation)
        staleActions(generation)
        assertTrue(entries.getTimedNotes(42).isEmpty()); assertTrue(entries.getRatingsFlow(42).first().isEmpty())
    }
    @Test fun missingDestinationChildAndUnitAreRejectedWithoutChangingIngestion() = runBlocking<Unit> {
        val generation = guard.currentGeneration()
        rejected { entries.update(ingestion.copy(experienceId=999),42,generation) }
        rejected { entries.update(ingestion.copy(customUnitId=999),42,generation) }
        try { entries.update(ingestion.copy(endTime=Instant.EPOCH.minusSeconds(1)),42,generation); fail("Invalid end time must be rejected") }
        catch (_: IllegalArgumentException) { }
        db.experienceDao().delete(ingestion)
        rejected { entries.update(ingestion,42,generation) }
        assertNull(entries.getIngestionFlow(1).first())
    }
    @Test fun noteOnlyUpdateRetainsDoseBitsInRoom() = runBlocking<Unit> {
        val generation = guard.currentGeneration()
        entries.update(ingestion.copy(notes="changed"),42,generation)
        val actual = entries.getIngestionFlow(1).first()!!.ingestion
        assertEquals(ingestion.dose!!.toRawBits(),actual.dose!!.toRawBits())
        assertEquals(ingestion.estimatedDoseStandardDeviation!!.toRawBits(),actual.estimatedDoseStandardDeviation!!.toRawBits())
    }
    @Test fun ingestionEditorNoteOnlySavePreservesUnflaggedUncertaintyAndMissingUnitIsNotSilentlyCleared() = runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val preferenceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val store=androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope=preferenceScope) { File(directory,"editor-preferences.preferences_pb") }
        val preferences=com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences(store)
        try {
            db.experienceDao().update(ingestion.copy(isDoseAnEstimate=false))
            lateinit var vm: com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionViewModel
            instrumentation.runOnMainSync { vm=com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionViewModel(entries,preferences,guard,SavedStateHandle(mapOf("ingestionId" to 1))) }
            withTimeout(10000) { while(!vm.session.ready) delay(10) }
            val done=CompletableDeferred<Unit>()
            instrumentation.runOnMainSync { vm.note="only note changed"; vm.onDoneTap { done.complete(Unit) } }
            withTimeout(10000) { done.await() }
            val saved=entries.getIngestionFlow(1).first()!!.ingestion
            assertEquals(ingestion.dose!!.toRawBits(),saved.dose!!.toRawBits())
            assertEquals(ingestion.estimatedDoseStandardDeviation!!.toRawBits(),saved.estimatedDoseStandardDeviation!!.toRawBits())
            db.experienceDao().update(saved.copy(customUnitId=999))
            lateinit var missing: com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionViewModel
            instrumentation.runOnMainSync { missing=com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionViewModel(entries,preferences,guard,SavedStateHandle(mapOf("ingestionId" to 1))) }
            withTimeout(10000) { while(!missing.session.ready) delay(10) }
            var closed=false
            instrumentation.runOnMainSync { missing.note="must not clear unit"; missing.onDoneTap { closed=true } }
            withTimeout(10000) { while(missing.session.error==null) delay(10) }
            assertFalse(closed); assertEquals(999,entries.getIngestionFlow(1).first()!!.ingestion.customUnitId)
            db.experienceDao().delete(saved)
            instrumentation.runOnMainSync { missing.session.dismissError(); missing.saveClonedIngestionTime { closed=true } }
            withTimeout(10000) { while(missing.session.busy) delay(10) }
            assertFalse(closed); assertTrue(missing.session.error!!.contains("deleted"))
            androidx.lifecycle.ViewModelStore().apply { put("first",vm); put("missing",missing); clear() }
        } finally { preferenceScope.cancel() }
    }

    @Test fun customUnitNoteOnlyEditorSaveRetainsExactDoseAndUnflaggedUncertainty() = runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val original=CustomUnit.twoCBSample.copy(id=6,dose=0.0001,estimatedDoseStandardDeviation=1.234567,isEstimate=false)
        db.experienceDao().insert(original)
        val substances=com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository(instrumentation.targetContext,
            com.isaakhanimann.journal.data.substances.parse.SubstanceParser())
        substances.initialize()
        lateinit var vm: com.isaakhanimann.journal.ui.tabs.settings.customunits.edit.EditCustomUnitViewModel
        instrumentation.runOnMainSync { vm=com.isaakhanimann.journal.ui.tabs.settings.customunits.edit.EditCustomUnitViewModel(entries,substances,guard,SavedStateHandle(mapOf("customUnitId" to 6))) }
        withTimeout(10000) { while(!vm.canSave) delay(10) }
        val done=CompletableDeferred<Unit>()
        instrumentation.runOnMainSync { vm.note="note changed"; vm.updateAndDismissAfter { done.complete(Unit) } }
        withTimeout(10000) { done.await() }
        val actual=entries.getCustomUnit(6)!!
        assertEquals(original.dose!!.toRawBits(),actual.dose!!.toRawBits())
        assertEquals(original.estimatedDoseStandardDeviation!!.toRawBits(),actual.estimatedDoseStandardDeviation!!.toRawBits())
        androidx.lifecycle.ViewModelStore().apply { put("unit",vm); clear() }
    }

    @Test fun snapshotBindsRecoveredFormsToGenerationAndConcurrentActionsRunOnce() = runBlocking<Unit> {
        val snapshot = attachments.editorSnapshot(42)
        assertEquals(snapshot.generation,guard.currentGeneration()); assertEquals("original",snapshot.entry.title)
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val session = JournalEditorSession(scope,guard,SavedStateHandle())
            session.load { checkNotNull(entries.getExperience(42)) }
            withTimeout(10000) { while(session.loading) delay(10) }
            var count=0; var finished=false
            val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            session.run({finished=true}) { count++; entered.complete(Unit); release.await() }
            entered.await()
            session.run { count++ }
            assertEquals(1,count); assertFalse(finished)
            release.complete(Unit)
            withTimeout(10000) { while(session.busy) delay(10) }
            assertTrue(finished)
            db.photoAttachmentDao().setGeneration(JournalState(generation="restored"))
            val recovered = JournalEditorSession(scope,guard,SavedStateHandle(mapOf("journalGeneration" to snapshot.generation)))
            recovered.load { fail("Stale saved state must not load reused ID") }
            withTimeout(10000) { while(recovered.error==null) delay(10) }
            assertFalse(recovered.ready)
        } finally { scope.cancel() }
    }
    @Test fun editorSnapshotsNeverMixEntryAndGenerationDuringReplacement() = runBlocking<Unit> {
        db.withTransaction {
            db.photoAttachmentDao().setGeneration(JournalState(generation="snapshot-initial"))
            db.experienceDao().update(entry.copy(title="snapshot-initial"))
        }
        coroutineScope {
            val writer=launch(Dispatchers.Default) {
                repeat(20) { index -> db.withTransaction {
                    db.photoAttachmentDao().setGeneration(JournalState(generation="snapshot-$index"))
                    delay(2)
                    db.experienceDao().update(entry.copy(title="snapshot-$index"))
                } }
            }
            repeat(30) { val value=attachments.editorSnapshot(42); assertEquals(value.generation,value.entry.title) }
            writer.join()
        }
    }

    @Test fun failureAndCancellationKeepEditorOpenAndRollbackWrites() = runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val session=JournalEditorSession(scope,guard,SavedStateHandle())
        try {
            session.load { checkNotNull(entries.getExperience(42)) }
            withTimeout(10000) { while(session.loading) delay(10) }
            var closed=false
            session.run({closed=true}) { guard.withSession(session.generation) { db.experienceDao().updateFavorite(42,true); error("injected write failure") } }
            withTimeout(10000) { while(session.busy) delay(10) }
            assertFalse(closed); assertNotNull(session.error); assertFalse(entries.getExperience(42)!!.isFavorite)
            val failingPreferences = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
                override val data = kotlinx.coroutines.flow.flowOf(androidx.datastore.preferences.core.emptyPreferences())
                override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
                    throw java.io.IOException("injected preference failure")
                }
            }
            val preferences=com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences(failingPreferences)
            session.run({closed=true}) { preferences.saveIngestionTimes(Instant.EPOCH,null) }
            withTimeout(10000) { while(session.busy) delay(10) }
            assertFalse(closed); assertTrue(session.error!!.contains("preference"))
            val entered=CompletableDeferred<Unit>()
            session.run({closed=true}) { guard.withSession(session.generation) { db.experienceDao().updateFavorite(42,true); entered.complete(Unit); awaitCancellation() } }
            entered.await(); scope.cancel(); scope.coroutineContext[Job]!!.join()
            assertFalse(closed); assertFalse(session.busy); assertFalse(entries.getExperience(42)!!.isFavorite)
        } finally { scope.cancel() }
    }
}
