package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID
import java.io.File
import android.net.Uri

@RunWith(AndroidJUnit4::class)
class DefinitionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var guard: JournalSessionGuard
    private lateinit var repo: DefinitionRepository
    private lateinit var tags: TagRepository
    private lateinit var generation: String
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        guard = JournalSessionGuard(db, JournalMutationLock()); repo = DefinitionRepository(db, guard); tags = TagRepository(db, guard)
        generation = guard.readSession(null) { it }
    }
    @After fun cleanup() { db.close() }
    private fun unit() = CustomUnit(0, null, "Synthetic", Instant.EPOCH, null, null, null, false, false, "capsule", null, null, "")
    private suspend fun create(): Int = repo.save(generation, unit(), listOf(DoseComponent(parentCustomUnitId = 0,
        substanceName = "Synthetic leaf", substanceUnit = "mg", dose = 0.0001)), emptyList(), null)

    @Test fun failedAndConflictingSavesKeepTheOriginalGraph() = runBlocking {
        val id = create()
        val original = db.definitionDao().definition(id)!!
        repo.save(generation, original.unit.copy(note = "first"), original.components, emptyList(), original)
        try { repo.save(generation, original.unit.copy(note = "stale"), original.components, emptyList(), original); fail("Stale write accepted") }
        catch (_: IllegalStateException) { }
        val current = db.definitionDao().definition(id)!!
        assertEquals("first", current.unit.note)
        try { repo.save(generation, current.unit, listOf(DoseComponent(parentCustomUnitId = id, childCustomUnitId = id, dose = 1.0)), emptyList(), current); fail("Cycle accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(current, db.definitionDao().definition(id))
    }
    @Test fun referencedDefinitionsCannotBeDeletedAndRestoreInvalidatesSessions() = runBlocking {
        val child = create()
        repo.save(generation, unit().copy(name = "Parent"), listOf(DoseComponent(parentCustomUnitId = 0, childCustomUnitId = child, dose = 1.0)), emptyList(), null)
        val original = db.definitionDao().definition(child)!!
        try { repo.delete(generation, original); fail("Referenced definition deleted") } catch (_: IllegalArgumentException) { }
        assertEquals(2, repo.snapshot().size)
        db.experienceDao().deleteEverythingAndInvalidate(UUID.randomUUID().toString())
        try { repo.save(generation, unit(), emptyList(), emptyList(), null); fail("Old generation accepted") } catch (_: StaleJournalSession) { }
        assertTrue(repo.snapshot().isEmpty())
    }
    @Test fun legacyEditsKeepTheComponentAndEffectiveAmountInSync() = runBlocking {
        val legacy = unit().copy(substanceName = "Synthetic leaf", administrationRoute = AdministrationRoute.ORAL,
            dose = 1.234567, originalUnit = "mg")
        val id = db.experienceDao().insertLegacyUnit(legacy)
        val current = db.experienceDao().getCustomUnit(id)!!
        db.experienceDao().updateLegacyUnit(current.copy(dose = 0.0001))
        val snapshot = repo.snapshot()
        assertEquals(0.0002, snapshot.toGraph().resolve(id, DoseQuantity(2.0)).single().quantity.amount!!, 0.0)
        assertEquals(listOf(AdministrationRoute.ORAL), snapshot.toGraph().availableRoutes(id) { emptyList() })
    }
    @Test fun tagDuplicateValidationPrimaryReplacementAndDeletionPreserveSubjects() = runBlocking {
        val a = SubstanceTag(UUID.randomUUID().toString(), "Café", "BLUE")
        val b = SubstanceTag(UUID.randomUUID().toString(), "Beta", "RED")
        tags.save(generation, a, null); tags.save(generation, b, null)
        try { tags.save(generation, a.copy(id = UUID.randomUUID().toString(), name = " CAFE "), null); fail("Duplicate normalized name accepted") }
        catch (_: IllegalArgumentException) { }
        val subject = SubjectId.Definition(create())
        tags.assign(generation, subject, setOf(a.id, b.id), a.id)
        tags.delete(generation, a)
        val remaining = db.definitionDao().memberships().single()
        assertEquals(b.id, remaining.tagId); assertTrue(remaining.isPrimary)
        assertEquals(1, repo.snapshot().size)
    }

    @Test fun libraryMergePreservesExistingDefinitionsAndReviewsTagConflicts() = runBlocking {
        val id = create()
        val tag = SubstanceTag(UUID.randomUUID().toString(), "Library tag", "BLUE")
        tags.save(generation, tag, null)
        tags.assign(generation, SubjectId.Definition(id), setOf(tag.id), tag.id)
        val original = db.definitionDao().definition(id)!!
        val text = DefinitionLibraryCodec.encode(repo.snapshot(), setOf(id), db.definitionDao().tags(), db.definitionDao().memberships()).replace("BLUE", "RED")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "library-${UUID.randomUUID()}.json")
        try {
            file.writeText(text)
            val exchange = DefinitionLibraryExchange(db, guard, context)
            val preview = exchange.preview(Uri.fromFile(file), generation)
            assertTrue(preview.messages.any { it.contains("existing tag") })
            assertEquals(1, exchange.merge(preview))
            assertEquals(original, db.definitionDao().definition(id))
            assertEquals(2, repo.snapshot().size)
            val imported = repo.snapshot().single { it.unit.id != id }
            assertEquals(original.unit.name, imported.unit.name)
            assertEquals(0.0001, imported.components.single().dose!!, 0.0)
            val allTags = db.definitionDao().tags()
            assertEquals(tag, allTags.single { it.id == tag.id })
            val fork = allTags.single { it.id != tag.id }
            assertEquals("RED", fork.color); assertTrue(fork.name.contains("imported"))
            assertEquals(fork.id, db.definitionDao().memberships().single { it.subjectId == "custom:${imported.unit.id}" }.tagId)
            assertEquals(text, file.readText())
        } finally { file.delete() }
    }

    @Test fun changedLibraryPreviewCannotOverwriteNewerData() = runBlocking {
        val id = create()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "library-stale-${UUID.randomUUID()}.json")
        try {
            file.writeText(DefinitionLibraryCodec.encode(repo.snapshot(), setOf(id), emptyList(), emptyList()))
            val exchange = DefinitionLibraryExchange(db, guard, context)
            val preview = exchange.preview(Uri.fromFile(file), generation)
            val original = db.definitionDao().definition(id)!!
            repo.save(generation, original.unit.copy(note = "newer"), original.components, original.roaInfos, original)
            try { exchange.merge(preview); fail("Stale import preview accepted") } catch (_: IllegalStateException) { }
            assertEquals(1, repo.snapshot().size)
            assertEquals("newer", repo.snapshot().single().unit.note)
        } finally { file.delete() }
    }
}
