package com.isaakhanimann.journal.psyzine

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.settings.definitions.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DefinitionEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var guard: JournalSessionGuard
    private lateinit var repo: DefinitionRepository
    private lateinit var entries: ExperienceRepository
    private lateinit var substances: SubstanceRepository
    private lateinit var directory: File
    private val models = ViewModelStore()
    private var sequence = 0
    @Before fun setup() {
        Assume.assumeTrue("Synthetic emulator UI fixture only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(app.cacheDir, "definitions-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(db, lock); repo = DefinitionRepository(db, guard)
        entries = ExperienceRepository(db.experienceDao(), db.photoAttachmentDao(), AttachmentStorage(context), lock, guard)
        substances = SubstanceRepository(context, SubstanceParser())
    }
    @After fun cleanup() {
        if (::db.isInitialized) {
            compose.runOnUiThread { models.clear() }
            db.close(); directory.deleteRecursively()
        }
    }
    private fun model(state: SavedStateHandle = SavedStateHandle(mapOf("definitionId" to 0))): DefinitionEditorViewModel {
        lateinit var result: DefinitionEditorViewModel
        compose.runOnUiThread {
            result = DefinitionEditorViewModel(repo, guard, substances, entries, Clock.systemUTC(), state)
            models.put("editor${sequence++}", result)
        }
        compose.waitUntil(15000) { result.ready || result.error != null }
        return result
    }
    private fun show(model: DefinitionEditorViewModel, saved: AtomicInteger, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PsyZineTheme { DefinitionEditorScreen(back = { saved.incrementAndGet() }, model = model) }
            }
        }
    }
    @Test fun blankSaveAtLargeFontExplainsErrorsWithoutCreatingRecords() {
        val model = model(); assertTrue(model.error, model.ready)
        show(model, AtomicInteger(), 1.3f)
        compose.onNodeWithText("Save definition").performClick()
        compose.waitUntil { model.errors.containsKey("name") }
        compose.onNodeWithText("Enter a name for this definition.").assertIsDisplayed()
        assertTrue(runBlocking { repo.snapshot() }.isEmpty())
    }
    @Test fun componentValidationAndSaveWorkAtTwoHundredPercentFont() {
        val model = model(); assertTrue(model.error, model.ready)
        val saved = AtomicInteger()
        show(model, saved, 2f)
        compose.onNodeWithTag("definition-field:name").performTextInput("Synthetic large text blend")
        compose.onNodeWithTag("definition-form").performScrollToNode(hasTestTag("definition-field:unit"))
        compose.onNodeWithTag("definition-field:unit").performTextInput("capsule")
        compose.onNodeWithTag("definition-form").performScrollToNode(hasText("Add component"))
        compose.onNodeWithText("Add component").performClick()
        compose.onNodeWithText("Search substances and definitions").performTextInput("Caffeine")
        compose.onNode(hasText("Caffeine") and hasClickAction() and !hasSetTextAction()).performClick()
        val key = model.draft.components.single().key
        compose.onNodeWithTag("definition-form").performScrollToNode(hasTestTag("definition-field:$key.dose"))
        compose.onNodeWithTag("definition-field:$key.dose").performTextInput("NaN")
        compose.onNodeWithText("Save definition").performClick()
        compose.waitUntil { model.errors.containsKey("$key.dose") }
        compose.onNodeWithText("Enter a number that is not too large; NaN and Infinity are not valid.").assertIsDisplayed()
        compose.onNodeWithTag("definition-field:$key.dose").performTextReplacement("0.0001")
        compose.onNodeWithText("Save definition").performClick()
        compose.waitUntil(15000) { saved.get() > 0 || model.error != null }
        assertNull(model.error); assertEquals(1, saved.get())
        compose.onNodeWithText("Saved").assertIsNotEnabled()
        val snapshot = runBlocking { repo.snapshot() }
        assertEquals(1, snapshot.size)
        assertEquals(0.0002, snapshot.toGraph().resolve(snapshot.single().unit.id, DoseQuantity(2.0)).single().quantity.amount!!, 0.0)
    }
    @Test fun draftsRecoverWithinGenerationAndRejectReusedIdsAfterRestore() {
        val state = SavedStateHandle(mapOf("definitionId" to 0))
        val first = model(state)
        compose.runOnIdle { first.change(first.draft.copy(name = "Kept draft", unit = "capsule", note = "kept note")) }
        fun recovered() = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
        val next = model(recovered())
        assertTrue(next.ready); assertEquals(first.draft, next.draft)
        runBlocking { db.experienceDao().deleteEverythingAndInvalidate(UUID.randomUUID().toString()) }
        val stale = model(recovered())
        assertFalse(stale.ready); assertTrue(stale.error!!.contains("restored or deleted"))
        assertTrue(runBlocking { repo.snapshot() }.isEmpty())
    }
}
