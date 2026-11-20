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
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timednote.add.AddTimedNoteViewModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timednote.edit.EditTimedNoteViewModel
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDateTime

class TimedNoteDraftRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var repository: ExperienceRepository
    private lateinit var guard: JournalSessionGuard
    private val store = ViewModelStore()
    private val experienceId = 51

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(database, lock)
        repository = ExperienceRepository(database.experienceDao(), database.photoAttachmentDao(), AttachmentStorage(context), lock, guard)
        runBlocking { database.experienceDao().insert(Experience(experienceId, "Synthetic draft test", "", sortDate = Instant.now(), location = null)) }
    }
    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        database.close()
    }
    private fun recover(state: SavedStateHandle) = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })

    @Test fun newNoteRecoversAllFieldsAndImmediateSaveUsesTheNewTime() {
        val state = SavedStateHandle(mapOf("experienceId" to experienceId))
        val first = compose.runOnIdle { AddTimedNoteViewModel(repository, guard, state).also { store.put("note", it) } }
        compose.waitUntil(10000) { first.session.ready }
        val selected = LocalDateTime.of(2026, 11, 20, 15, 42)
        val restored = compose.runOnIdle {
            first.onChangeNote("")
            first.onChangeColor(AdaptiveColor.OLIVE)
            first.onChangeIsPartOfTimeline(false)
            first.onChangeTime(selected)
            val recovered = recover(state)
            store.clear()
            AddTimedNoteViewModel(repository, guard, recovered).also { store.put("note", it) }
        }
        compose.waitUntil(10000) { restored.session.ready }
        var completed = 0
        compose.runOnIdle {
            assertEquals("", restored.note)
            assertEquals(AdaptiveColor.OLIVE, restored.color)
            assertFalse(restored.isPartOfTimeline)
            assertEquals(selected, restored.localDateTimeFlow.value)
            restored.onChangeNote("Synthetic new note")
            restored.onChangeTime(selected.plusMinutes(1))
            restored.onDoneTap { completed++ }
            restored.onDoneTap { completed++ }
        }
        compose.waitUntil(10000) { !restored.session.busy }
        compose.runOnIdle { assertEquals(1, completed) }
        val saved = runBlocking { repository.getTimedNotes(experienceId).single() }
        assertEquals(selected.plusMinutes(1).getInstant(), saved.time)
        assertEquals("Synthetic new note", saved.note)
        assertEquals(AdaptiveColor.OLIVE, saved.color)
        assertFalse(saved.isPartOfTimeline)
    }

    @Test fun editedNoteRecoversDraftWithoutChangingItsUneditedRecordedInstant() {
        val original = TimedNote(61, time = Instant.parse("2026-10-25T01:30:00Z"), note = "Original", color = AdaptiveColor.BLUE,
            experienceId = experienceId, isPartOfTimeline = true)
        runBlocking { database.experienceDao().insert(original) }
        val state = SavedStateHandle(mapOf("experienceId" to experienceId, "timedNoteId" to original.id))
        val first = compose.runOnIdle { EditTimedNoteViewModel(repository, guard, state).also { store.put("note", it) } }
        compose.waitUntil(10000) { first.session.ready }
        val restored = compose.runOnIdle {
            first.onChangeNote("Recovered edit")
            first.onChangeColor(AdaptiveColor.OLIVE)
            first.onChangeIsPartOfTimeline(false)
            val recovered = recover(state)
            store.clear()
            EditTimedNoteViewModel(repository, guard, recovered).also { store.put("note", it) }
        }
        compose.waitUntil(10000) { restored.session.ready }
        compose.runOnIdle {
            assertEquals("Recovered edit", restored.note)
            assertEquals(original.time.getLocalDateTime(), restored.localDateTimeFlow.value)
            assertEquals(AdaptiveColor.OLIVE, restored.color)
            assertFalse(restored.isPartOfTimeline)
            restored.onDoneTap {}
        }
        compose.waitUntil(10000) { !restored.session.busy }
        val saved = runBlocking { repository.getTimedNote(original.id) }!!
        assertEquals(original.time, saved.time)
        assertEquals("Recovered edit", saved.note)
        assertEquals(AdaptiveColor.OLIVE, saved.color)
        assertFalse(saved.isPartOfTimeline)
    }
}
