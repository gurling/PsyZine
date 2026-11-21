// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import com.isaakhanimann.journal.ui.utils.JournalEditorGate
import com.isaakhanimann.journal.ui.utils.JournalEditorSession
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class JournalEditorRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var session: JournalEditorSession

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        session = JournalEditorSession(scope, JournalSessionGuard(database, JournalMutationLock()), SavedStateHandle())
    }
    @After fun cleanup() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        database.close()
    }
    private fun render(back: () -> Unit = {}) {
        compose.setContent { PsyZineTheme { if (JournalEditorGate(session, back)) Text("Draft controls") } }
    }
    private fun loadReady() {
        compose.runOnIdle { session.load {} }
        compose.waitUntil(10000) { session.ready }
    }

    @Test fun failedLoadRetainsItsReasonAfterDismissAndOffersBackInsteadOfFormControls() {
        var backs = 0
        render { backs++ }
        compose.runOnIdle { session.load { error("Synthetic entry was deleted. Close this screen.") } }
        compose.waitUntil(10000) { !session.loading && session.error != null }
        compose.onNodeWithText("Entry unavailable").assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Synthetic entry was deleted. Close this screen.").assertExists()
        compose.onNodeWithText("Draft controls").assertDoesNotExist()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs); assertNull(session.error); assertNotNull(session.unavailableMessage) }
    }

    @Test fun generationReplacementRemovesDraftControlsAndRetainsTheRecoveryMessage() {
        render()
        loadReady()
        compose.onNodeWithText("Draft controls").assertExists()
        runBlocking { database.photoAttachmentDao().setGeneration(JournalState(generation = "replacement")) }
        compose.waitUntil(10000) { !session.ready && session.error != null }
        val reason = compose.runOnIdle { checkNotNull(session.unavailableMessage) }
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(reason).assertExists()
        compose.onNodeWithText("Draft controls").assertDoesNotExist()
        compose.onNodeWithText("Back").assertExists()
        compose.runOnIdle {
            var ran = false
            session.run { ran = true }
            assertFalse(ran)
        }
    }

    @Test fun ordinaryFailedSaveKeepsTheValidDraftAvailableAfterDismiss() {
        render()
        loadReady()
        compose.runOnIdle { session.run { throw IOException("Synthetic write failed. Try again.") } }
        compose.waitUntil(10000) { !session.busy && session.error != null }
        compose.onNodeWithText("Could not save changes").assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Draft controls").assertExists()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.runOnIdle { assertTrue(session.ready); assertNull(session.unavailableMessage) }
    }

    @Test fun customSubstanceDraftSurvivesRecreationAndSearchNameDoesNotOverwriteEdits() {
        val state = SavedStateHandle()
        val store = androidx.lifecycle.ViewModelStore()
        fun model(handle: SavedStateHandle): com.isaakhanimann.journal.ui.tabs.search.custom.AddCustomSubstanceViewModel {
            val guard = JournalSessionGuard(database, JournalMutationLock())
            val repository = com.isaakhanimann.journal.data.room.experiences.ExperienceRepository(
                database.experienceDao(), database.photoAttachmentDao(),
                com.isaakhanimann.journal.data.attachments.AttachmentStorage(
                    InstrumentationRegistry.getInstrumentation().targetContext), JournalMutationLock(), guard)
            return com.isaakhanimann.journal.ui.tabs.search.custom.AddCustomSubstanceViewModel(repository, guard, handle)
        }
        lateinit var original: com.isaakhanimann.journal.ui.tabs.search.custom.AddCustomSubstanceViewModel
        lateinit var restored: com.isaakhanimann.journal.ui.tabs.search.custom.AddCustomSubstanceViewModel
        try {
            compose.runOnIdle {
                original = model(state)
                store.put("original", original)
                original.initializeName("Search suggestion")
                original.name = ""
                original.units = "mL"
                original.description = "Synthetic draft"
                original.initializeName("Search suggestion")
                assertEquals("", original.name)
            }
            compose.waitUntil(10000) { original.session.ready }
            compose.runOnIdle {
                val recovered = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
                store.clear()
                restored = model(recovered)
                store.put("restored", restored)
                restored.initializeName("Search suggestion")
                assertEquals("", restored.name)
                assertEquals("mL", restored.units)
                assertEquals("Synthetic draft", restored.description)
                assertFalse(restored.isValid)
            }
            compose.waitUntil(10000) { restored.session.ready }
            runBlocking { database.photoAttachmentDao().setGeneration(JournalState(generation = "changed-draft")) }
            compose.waitUntil(10000) { !restored.session.ready }
            compose.runOnIdle {
                restored.name = "Synthetic name"
                restored.addCustomSubstance { fail("Stale draft must not navigate") }
            }
            runBlocking { assertTrue(database.experienceDao().getAllCustomSubstances().isEmpty()) }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test fun customSubstanceFormExplainsMissingNameAndUnits() {
        compose.setContent {
            PsyZineTheme {
                com.isaakhanimann.journal.ui.tabs.search.custom.AddOrEditCustomSubstanceContent(
                    padding = androidx.compose.foundation.layout.PaddingValues(),
                    name = " ", onNameChange = {}, units = "", onUnitsChange = {},
                    description = "", onDescriptionChange = {}, showRequiredErrors = true)
            }
        }
        compose.onNodeWithText("Enter a name for this substance.").assertExists()
        compose.onNodeWithText("Enter a unit, such as mg or mL, or choose one below.").assertExists()
    }

}
