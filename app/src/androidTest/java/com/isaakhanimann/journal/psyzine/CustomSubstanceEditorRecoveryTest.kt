// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.AttachmentStorage
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import com.isaakhanimann.journal.ui.tabs.search.custom.EditCustomSubstanceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class CustomSubstanceEditorRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var lock: JournalMutationLock
    private lateinit var repository: ExperienceRepository
    private lateinit var guard: JournalSessionGuard
    private val store = ViewModelStore()
    private val original = CustomSubstance(42, "Synthetic substance", "mg", "Stored description")

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        lock = JournalMutationLock()
        guard = JournalSessionGuard(database, lock)
        repository = ExperienceRepository(database.experienceDao(), database.photoAttachmentDao(), AttachmentStorage(context), lock, guard)
        runBlocking { database.experienceDao().insert(original) }
    }

    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        database.close()
    }

    private fun create(state: SavedStateHandle): EditCustomSubstanceViewModel =
        compose.runOnIdle {
            EditCustomSubstanceViewModel(repository, guard, state).also { store.put("editor", it) }
        }

    @Test fun recreatedDraftRetainsBlankFieldsAndSaveUsesOneCapturedSnapshot() {
        val state = SavedStateHandle(mapOf("customSubstanceId" to original.id))
        val first = create(state)
        compose.waitUntil(10000) { first.session.ready }
        lateinit var recovered: SavedStateHandle
        compose.runOnIdle {
            first.name = ""
            first.units = "mL"
            first.description = "Unsaved description"
            recovered = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
            store.clear()
        }
        val restored = create(recovered)
        compose.waitUntil(10000) { restored.session.ready }
        compose.runOnIdle {
            assertEquals("", restored.name)
            assertEquals("mL", restored.units)
            assertEquals("Unsaved description", restored.description)
            assertFalse(restored.isValid)
            restored.name = "Edited substance"
        }
        var completed = 0
        runBlocking { lock.mutex.lock() }
        try {
            compose.runOnIdle {
                restored.onDoneTap { completed++ }
                assertTrue(restored.session.busy)
                restored.description = "Later typing"
                restored.onDoneTap { completed++ }
            }
        } finally { lock.mutex.unlock() }
        compose.waitUntil(10000) { !restored.session.busy }
        compose.runOnIdle { assertEquals(1, completed) }
        runBlocking {
            assertEquals(CustomSubstance(original.id, "Edited substance", "mL", "Unsaved description"), repository.getCustomSubstance(original.id))
        }
    }

    @Test fun recoveredDraftCannotEditOrDeleteReusedIdInReplacementJournal() {
        val state = SavedStateHandle(mapOf("customSubstanceId" to original.id))
        val first = create(state)
        compose.waitUntil(10000) { first.session.ready }
        lateinit var recovered: SavedStateHandle
        compose.runOnIdle {
            first.description = "Old journal draft"
            recovered = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
            store.clear()
        }
        val replacement = original.copy(description = "Replacement journal")
        runBlocking {
            database.experienceDao().insert(replacement)
            database.photoAttachmentDao().setGeneration(JournalState(generation = "replacement-journal"))
        }
        val restored = create(recovered)
        compose.waitUntil(10000) { !restored.session.loading }
        compose.runOnIdle {
            assertFalse(restored.session.ready)
            assertNotNull(restored.session.unavailableMessage)
            restored.onDoneTap { fail("Stale save must not navigate") }
            restored.deleteCustomSubstance { fail("Stale delete must not navigate") }
        }
        runBlocking { assertEquals(replacement, repository.getCustomSubstance(original.id)) }
    }
}
