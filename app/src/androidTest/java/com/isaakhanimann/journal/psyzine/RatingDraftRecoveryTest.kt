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
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.add.AddRatingViewModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.edit.EditRatingViewModel
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDateTime

class RatingDraftRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var repository: ExperienceRepository
    private lateinit var guard: JournalSessionGuard
    private val store = ViewModelStore()
    private val experienceId = 71

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(database, lock)
        repository = ExperienceRepository(database.experienceDao(), database.photoAttachmentDao(), AttachmentStorage(context), lock, guard)
        runBlocking { database.experienceDao().insert(Experience(experienceId, "Synthetic rating draft", "", sortDate = Instant.now(), location = null)) }
    }
    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        database.close()
    }
    private fun recover(state: SavedStateHandle) = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })

    @Test fun newRatingRecoversOverallChoiceAndImmediateTimedSaveUsesTheNewTime() {
        val state = SavedStateHandle(mapOf("experienceId" to experienceId))
        val first = compose.runOnIdle { AddRatingViewModel(repository, guard, state).also { store.put("rating", it) } }
        compose.waitUntil(10000) { first.session.ready }
        val selected = LocalDateTime.of(2026, 11, 21, 15, 42)
        val restored = compose.runOnIdle {
            first.onChangeRating(ShulginRatingOption.THREE_PLUS)
            first.changeOverallRating(true)
            first.changeHiddenInTimeline(true)
            first.onChangeTime(selected)
            val recovered = recover(state)
            store.clear()
            AddRatingViewModel(repository, guard, recovered).also { store.put("rating", it) }
        }
        compose.waitUntil(10000) { restored.session.ready }
        var completed = 0
        compose.runOnIdle {
            assertEquals(ShulginRatingOption.THREE_PLUS, restored.selectedRating)
            assertTrue(restored.isThisOverallRating)
            assertTrue(restored.isHiddenInTimeline)
            assertEquals(selected, restored.localDateTimeFlow.value)
            restored.changeOverallRating(false)
            restored.onChangeTime(selected.plusMinutes(1))
            restored.onDoneTap { completed++ }
            restored.onDoneTap { completed++ }
        }
        compose.waitUntil(10000) { !restored.session.busy }
        compose.runOnIdle { assertEquals(1, completed) }
        val saved = runBlocking { repository.getRatingsFlow(experienceId).first().single() }
        assertEquals(selected.plusMinutes(1).getInstant(), saved.time)
        assertEquals(ShulginRatingOption.THREE_PLUS, saved.option)
        assertTrue(saved.isHiddenInTimeline)
    }

    @Test fun editedRatingRecoversOptionAndVisibilityWithoutChangingItsRecordedInstant() {
        val original = ShulginRating(81, time = Instant.parse("2026-10-25T01:30:00Z"), option = ShulginRatingOption.TWO_PLUS,
            experienceId = experienceId)
        runBlocking { database.experienceDao().insert(original) }
        val state = SavedStateHandle(mapOf("ratingId" to original.id))
        val first = compose.runOnIdle { EditRatingViewModel(repository, guard, state).also { store.put("rating", it) } }
        compose.waitUntil(10000) { first.session.ready }
        val restored = compose.runOnIdle {
            first.onChangeRating(ShulginRatingOption.THREE_PLUS)
            first.changeHiddenInTimeline(true)
            val recovered = recover(state)
            store.clear()
            EditRatingViewModel(repository, guard, recovered).also { store.put("rating", it) }
        }
        compose.waitUntil(10000) { restored.session.ready }
        compose.runOnIdle {
            assertEquals(ShulginRatingOption.THREE_PLUS, restored.selectedRatingOption)
            assertEquals(original.time!!.getLocalDateTime(), restored.localDateTimeFlow.value)
            assertTrue(restored.isHiddenInTimeline)
            assertFalse(restored.isOverallRatingFlow.value)
            restored.onDoneTap {}
        }
        compose.waitUntil(10000) { !restored.session.busy }
        val saved = runBlocking { repository.getRating(original.id) }!!
        assertEquals(original.time, saved.time)
        assertEquals(ShulginRatingOption.THREE_PLUS, saved.option)
        assertTrue(saved.isHiddenInTimeline)
    }
}
