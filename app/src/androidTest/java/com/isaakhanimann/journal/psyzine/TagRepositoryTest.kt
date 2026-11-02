// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TagRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var guard: JournalSessionGuard
    private lateinit var repo: TagRepository
    private lateinit var generation: String
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        guard = JournalSessionGuard(db, JournalMutationLock()); repo = TagRepository(db, guard)
        generation = guard.readSession(null) { it }
    }
    @After fun cleanup() { db.close() }
    private suspend fun tag(name: String) = SubstanceTag(UUID.randomUUID().toString(), name, "BLUE").also { repo.save(generation, it, null) }
    private suspend fun snapshot(subject: SubjectId) = guard.readSession(generation) { repo.assignment(subject) }
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Stale mutation accepted") } catch (_: IllegalStateException) { }
    }
    @Test fun primarySelectionIsStableAndTypedSubjectsDoNotShareAssignments() = runBlocking {
        val a = tag("Alpha"); val b = tag("beta"); val c = tag("charlie")
        val catalog = SubjectId.Catalog("Same name"); val local = SubjectId.Local("Same name")
        db.experienceDao().insert(CustomSubstance(name = "Same name", units = "mg", description = ""))
        repo.assign(generation, catalog, setOf(b.id), null, snapshot(catalog))
        assertTrue(snapshot(catalog).memberships.single().isPrimary)
        repo.assign(generation, catalog, setOf(a.id, b.id, c.id), b.id, snapshot(catalog))
        assertEquals(b.id, snapshot(catalog).memberships.single { it.isPrimary }.tagId)
        repo.assign(generation, catalog, setOf(a.id, c.id), b.id, snapshot(catalog))
        assertEquals(a.id, snapshot(catalog).memberships.single { it.isPrimary }.tagId)
        assertTrue(snapshot(local).memberships.isEmpty())
        repo.assign(generation, local, setOf(c.id), c.id, snapshot(local))
        repo.assign(generation, catalog, emptySet(), null, snapshot(catalog))
        assertTrue(snapshot(catalog).memberships.isEmpty())
        assertEquals(c.id, snapshot(local).memberships.single().tagId)
    }
    @Test fun concurrentAssignmentAndTagDeletionNeverSilentlyOverwriteDrafts() = runBlocking {
        val a = tag("Alpha"); val b = tag("Beta"); val subject = SubjectId.Catalog("Missing metadata")
        val original = snapshot(subject)
        repo.assign(generation, subject, setOf(a.id), a.id, original)
        rejected { repo.assign(generation, subject, setOf(b.id), b.id, original) }
        assertEquals(a.id, snapshot(subject).memberships.single().tagId)
        val withA = snapshot(subject)
        repo.delete(generation, a)
        rejected { repo.assign(generation, subject, setOf(a.id), a.id, withA) }
        assertTrue(snapshot(subject).memberships.isEmpty())
    }
    @Test fun interruptedReplacementRollsBackDeletedMemberships() = runBlocking {
        val a = tag("Alpha"); val b = tag("Beta"); val subject = SubjectId.Catalog("Synthetic")
        repo.assign(generation, subject, setOf(a.id), a.id, snapshot(subject))
        val original = snapshot(subject)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_membership BEFORE INSERT ON SubstanceTagMembership BEGIN SELECT RAISE(ABORT, 'injected failed write'); END")
        try { repo.assign(generation, subject, setOf(b.id), b.id, original); fail("Injected write succeeded") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(original.memberships, snapshot(subject).memberships)
    }
    @Test fun restoredGenerationAndChangedLocalSubjectRejectAssignments() = runBlocking {
        val a = tag("Alpha"); val subject = SubjectId.Local("Synthetic")
        val id = db.experienceDao().insert(CustomSubstance(name = "Synthetic", units = "mg", description = "before")).toInt()
        val original = snapshot(subject)
        db.experienceDao().update(CustomSubstance(id, "Synthetic", "mg", "after"))
        rejected { repo.assign(generation, subject, setOf(a.id), a.id, original) }
        db.experienceDao().deleteEverythingAndInvalidate(UUID.randomUUID().toString())
        try { repo.assign(generation, subject, setOf(a.id), a.id, original); fail("Old generation accepted") }
        catch (_: StaleJournalSession) { }
        assertTrue(db.definitionDao().memberships().isEmpty())
    }
    @Test fun bulkAssignmentRetainsOtherTagsAndTheirPrimaryChoice() = runBlocking {
        val a = tag("Alpha"); val b = tag("Beta")
        val catalog = SubjectId.Catalog("Same name"); val local = SubjectId.Local("Same name")
        db.experienceDao().insert(CustomSubstance(name = "Same name", units = "mg", description = ""))
        repo.assign(generation, catalog, setOf(a.id), a.id, snapshot(catalog))
        val initial = db.definitionDao().memberships()
        val keys = setOf(catalog.key, local.key)
        val versions = guard.readSession(generation) { repo.subjectVersions(keys) }
        repo.assignSubjects(generation, b, keys, initial, versions)
        assertEquals(a.id, snapshot(catalog).memberships.single { it.isPrimary }.tagId)
        assertEquals(b.id, snapshot(local).memberships.single { it.isPrimary }.tagId)
        rejected { repo.assignSubjects(generation, b, emptySet(), initial, versions) }
        repo.assignSubjects(generation, b, emptySet(), db.definitionDao().memberships(), versions)
        assertEquals(a.id, snapshot(catalog).memberships.single().tagId)
        assertTrue(snapshot(local).memberships.isEmpty())
    }
    @Test fun bulkAssignmentFailureRollsBackEverySubject() = runBlocking {
        val tag = tag("Alpha")
        val first = SubjectId.Catalog("First"); val second = SubjectId.Catalog("Second")
        val keys = linkedSetOf(first.key, second.key)
        val versions = guard.readSession(generation) { repo.subjectVersions(keys) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_second BEFORE INSERT ON SubstanceTagMembership WHEN NEW.subjectId = 'pw:Second' BEGIN SELECT RAISE(ABORT, 'injected failed write'); END")
        try { repo.assignSubjects(generation, tag, keys, emptyList(), versions); fail("Injected write succeeded") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(db.definitionDao().memberships().isEmpty())
    }
}
