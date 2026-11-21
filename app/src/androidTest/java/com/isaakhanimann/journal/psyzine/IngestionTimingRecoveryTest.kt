// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.IngestionTimePickerOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionViewModel
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Duration
import java.time.Instant

class IngestionTimingRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var repository: ExperienceRepository
    private lateinit var guard: JournalSessionGuard
    private lateinit var preferences: UserPreferences
    private val store = ViewModelStore()
    private val original = Ingestion(id = 91, substanceName = "Synthetic", time = Instant.parse("2026-11-21T10:00:00Z"),
        creationDate = Instant.parse("2026-11-21T10:00:00Z"),
        administrationRoute = AdministrationRoute.ORAL, dose = 0.0001, isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = 0.00001, units = "mg", experienceId = 92,
        notes = "Synthetic timing test", stomachFullness = null, consumerName = null, customUnitId = null)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(database, lock)
        repository = ExperienceRepository(database.experienceDao(), database.photoAttachmentDao(), AttachmentStorage(context), lock, guard)
        preferences = UserPreferences(object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        })
        runBlocking {
            database.experienceDao().insert(Experience(original.experienceId, "Synthetic timing", "", sortDate = original.time, location = null))
            database.experienceDao().insert(original)
        }
    }
    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        database.close()
    }
    private fun create(state: SavedStateHandle) = compose.runOnIdle {
        EditIngestionViewModel(repository, preferences, guard, state).also { store.put("editor", it) }
    }

    @Test fun rangeDraftRecoversAndImmediateStartChangeMovesEndBeforeSaving() {
        val state = SavedStateHandle(mapOf("ingestionId" to original.id))
        val first = create(state)
        compose.waitUntil(10000) { first.session.ready }
        val selected = original.time.plusSeconds(7200).getLocalDateTime()
        lateinit var recovered: SavedStateHandle
        compose.runOnIdle {
            first.onChangeTimePickerOption(IngestionTimePickerOption.TIME_RANGE)
            first.onChangeStartTime(selected)
            recovered = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
            store.clear()
        }
        val restored = create(recovered)
        compose.waitUntil(10000) { restored.session.ready }
        var completed = 0
        compose.runOnIdle {
            assertEquals(IngestionTimePickerOption.TIME_RANGE, restored.ingestionTimePickerOptionFlow.value)
            assertEquals(selected, restored.localDateTimeStartFlow.value)
            assertEquals(Duration.ofMinutes(30), Duration.between(selected.getInstant(), restored.localDateTimeEndFlow.value.getInstant()))
            restored.onChangeStartTime(selected.plusHours(1))
            restored.onDoneTap { completed++ }
            restored.onDoneTap { completed++ }
        }
        compose.waitUntil(10000) { !restored.session.busy }
        compose.runOnIdle { assertEquals(1, completed) }
        val saved = runBlocking { repository.getIngestion(original.id) }!!
        assertEquals(selected.plusHours(1).getInstant(), saved.time)
        assertEquals(saved.time.plusSeconds(1800), saved.endTime)
        assertEquals(original.dose, saved.dose)
        assertEquals(original.estimatedDoseStandardDeviation, saved.estimatedDoseStandardDeviation)
    }

    @Test fun earlierEndMovesStartAndImmediatePointSelectionRemovesEnd() {
        val model = create(SavedStateHandle(mapOf("ingestionId" to original.id)))
        compose.waitUntil(10000) { model.session.ready }
        val end = original.time.minusSeconds(3600).getLocalDateTime()
        compose.runOnIdle {
            model.onChangeTimePickerOption(IngestionTimePickerOption.TIME_RANGE)
            model.onChangeEndTime(end)
            assertEquals(end.getInstant().minusSeconds(1800), model.localDateTimeStartFlow.value.getInstant())
            model.onChangeTimePickerOption(IngestionTimePickerOption.POINT_IN_TIME)
            model.onDoneTap {}
        }
        compose.waitUntil(10000) { !model.session.busy }
        val saved = runBlocking { repository.getIngestion(original.id) }!!
        assertEquals(end.getInstant().minusSeconds(1800), saved.time)
        assertNull(saved.endTime)
    }

    @Test fun recoveredFieldsKeepExactTextAndTheValidatedCustomUnitSelection() {
        val unit = CustomUnit.mdmaSample.copy(id = 101, substanceName = original.substanceName, dose = 1.0, creationDate = original.time)
        runBlocking { database.experienceDao().insert(unit) }
        val state = SavedStateHandle(mapOf("ingestionId" to original.id))
        val first = create(state)
        compose.waitUntil(10000) { first.session.ready }
        compose.runOnIdle { first.onChangeCustomUnit(unit) }
        compose.waitUntil(10000) { !first.session.busy }
        lateinit var recovered: SavedStateHandle
        compose.runOnIdle {
            first.note = ""
            first.onDoseChange("0.000000123456789")
            first.onChangeEstimatedDoseStandardDeviation("0.000000000123")
            first.onChangeIsEstimate(true)
            first.toggleIsKnown()
            first.onChangeConsumerName("Synthetic consumer")
            recovered = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
            store.clear()
        }
        val restored = create(recovered)
        compose.waitUntil(10000) { restored.session.ready || !restored.session.loading }
        compose.runOnIdle {
            assertTrue(restored.session.error, restored.session.ready)
            assertEquals("", restored.note)
            assertEquals("0.000000123456789", restored.dose)
            assertEquals("0.000000000123", restored.estimatedDoseStandardDeviation)
            assertTrue(restored.isEstimate)
            assertFalse(restored.isKnown)
            assertEquals("Synthetic consumer", restored.consumerName)
            assertEquals(unit.id, restored.customUnit?.id)
            assertEquals(unit.unit, restored.units)
            restored.toggleIsKnown()
            restored.onDoneTap {}
        }
        compose.waitUntil(10000) { !restored.session.busy }
        val saved = runBlocking { repository.getIngestion(original.id) }!!
        assertEquals(unit.id, saved.customUnitId)
        assertEquals("", saved.notes)
        assertEquals(0.000000123456789, saved.dose!!, 0.0)
        assertEquals(0.000000000123, saved.estimatedDoseStandardDeviation!!, 0.0)
        assertEquals("Synthetic consumer", saved.consumerName)
    }

    @Test fun recoveredCustomUnitMustMatchTheSelectionFingerprint() {
        val unit = CustomUnit.mdmaSample.copy(id = 101, substanceName = original.substanceName, dose = 1.0, creationDate = original.time)
        runBlocking { database.experienceDao().insert(unit) }
        val state = SavedStateHandle(mapOf("ingestionId" to original.id))
        val first = create(state)
        compose.waitUntil(10000) { first.session.ready }
        compose.runOnIdle { first.onChangeCustomUnit(unit) }
        compose.waitUntil(10000) { !first.session.busy }
        lateinit var recovered: SavedStateHandle
        compose.runOnIdle {
            recovered = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
            store.clear()
        }
        runBlocking { database.experienceDao().insert(unit.copy(dose = 2.0)) }
        val restored = create(recovered)
        compose.waitUntil(10000) { !restored.session.loading }
        compose.runOnIdle {
            assertFalse(restored.session.ready)
            assertTrue(restored.session.unavailableMessage.orEmpty().contains("selected custom unit changed"))
            restored.onDoneTap { fail("Changed selection must not save") }
        }
        assertEquals(original, runBlocking { repository.getIngestion(original.id) })
    }

}
