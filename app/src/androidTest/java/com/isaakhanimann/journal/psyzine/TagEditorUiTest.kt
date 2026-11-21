// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

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
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.ui.tabs.settings.tags.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance

class TagEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var guard: JournalSessionGuard
    private lateinit var repo: TagRepository
    private lateinit var generation: String
    private val models = ViewModelStore()
    private var sequence = 0
    @Before fun setup() = runBlocking {
        Assume.assumeTrue("Synthetic emulator UI fixture only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        guard = JournalSessionGuard(db, JournalMutationLock()); repo = TagRepository(db, guard)
        generation = guard.readSession(null) { it }
    }
    @After fun cleanup() {
        if (::db.isInitialized) { compose.runOnUiThread { models.clear() }; db.close() }
    }
    private fun editor(state: SavedStateHandle = SavedStateHandle()): TagEditorViewModel {
        lateinit var model: TagEditorViewModel
        compose.runOnUiThread {
            model = TagEditorViewModel(repo, guard, Clock.systemUTC(), state)
            models.put("editor${sequence++}", model)
        }
        compose.waitUntil(15000) { model.ready || model.error != null }
        return model
    }
    private suspend fun tag(name: String) = SubstanceTag(UUID.randomUUID().toString(), name, "BLUE").also { repo.save(generation, it, null) }
    @Test fun validationAtTwoHundredPercentFontKeepsDraftAndRejectsAccentDuplicates() {
        runBlocking { tag("Café") }
        val model = editor(); assertTrue(model.error, model.ready)
        val saved = AtomicInteger()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PsyZineTheme { TagEditorScreen(back = { saved.incrementAndGet() }, model = model) }
            }
        }
        compose.onNodeWithText("Save tag").performClick()
        compose.onNodeWithText("Enter a name for this tag.").assertIsDisplayed()
        compose.onNodeWithTag("tag-name").performTextInput(" CAFE ")
        compose.onNodeWithText("Save tag").performClick()
        compose.onNodeWithText("A tag with this name already exists, ignoring accents and case. Choose another name.").assertIsDisplayed()
        assertEquals(0, saved.get())
        compose.onNodeWithTag("tag-name").performTextReplacement("New synthetic tag")
        compose.onNodeWithText("Save tag").performClick()
        compose.waitUntil(15000) { saved.get() > 0 || model.error != null }
        assertNull(model.error); assertEquals(1, saved.get())
        assertEquals(2, runBlocking { db.definitionDao().tags().size })
        compose.onNodeWithText("Saved").assertIsNotEnabled()
    }
    @Test fun newTagDraftRecoversAndRestoreInvalidatesIt() {
        val state = SavedStateHandle()
        val first = editor(state)
        compose.runOnIdle { first.name("Kept draft"); first.color("UNKNOWN_IMPORTED_COLOR") }
        fun recovered() = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
        val next = editor(recovered())
        assertTrue(next.isNew); assertTrue(next.error, next.ready)
        assertEquals("Kept draft", next.name); assertEquals("UNKNOWN_IMPORTED_COLOR", next.color)
        runBlocking { db.experienceDao().deleteEverythingAndInvalidate(UUID.randomUUID().toString()) }
        val stale = editor(recovered())
        assertFalse(stale.ready); assertTrue(stale.error!!.contains("restored or deleted"))
    }
    @Test fun subjectAssignmentsAtLargeFontSavePrimaryAndSurviveRecreation() {
        val a = runBlocking { tag("Alpha synthetic tag with a long label") }
        val b = runBlocking { tag("Beta") }
        val state = SavedStateHandle(mapOf("subjectKey" to SubjectId.Catalog("Missing metadata").key))
        lateinit var model: SubjectTagsViewModel
        compose.runOnUiThread { model = SubjectTagsViewModel(repo, guard, state); models.put("subject", model) }
        compose.waitUntil(15000) { model.ready || model.error != null }
        assertTrue(model.error, model.ready)
        val saved = AtomicInteger()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PsyZineTheme { SubjectTagsScreen(back = { saved.incrementAndGet() }, manageTags = {}, model = model) }
            }
        }
        compose.onNodeWithTag("subject-tags").performScrollToNode(hasText(a.name))
        compose.onNode(hasText(a.name) and isToggleable()).performClick()
        compose.onNodeWithTag("subject-tags").performScrollToNode(hasText(b.name))
        compose.onNode(hasText(b.name) and isToggleable()).performClick()
        compose.onNodeWithContentDescription("Make Beta primary").performClick()
        assertEquals(b.id, model.primary)
        lateinit var recovered: SubjectTagsViewModel
        compose.runOnUiThread {
            recovered = SubjectTagsViewModel(repo, guard, SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
            models.put("recovered", recovered)
        }
        compose.waitUntil(15000) { recovered.ready || recovered.error != null }
        assertEquals(model.selected, recovered.selected); assertEquals(b.id, recovered.primary)
        compose.onNodeWithText("Save tags").performClick()
        compose.waitUntil(15000) { saved.get() > 0 || model.error != null }
        assertNull(model.error); assertEquals(1, saved.get())
        val rows = runBlocking { db.definitionDao().memberships() }
        assertEquals(setOf(a.id, b.id), rows.map { it.tagId }.toSet()); assertEquals(b.id, rows.single { it.isPrimary }.tagId)
    }
    @Test fun bulkSubjectPickerDistinguishesLocalAndCatalogNamesAtLargeFont() {
        val tag = runBlocking {
            db.experienceDao().insert(CustomSubstance(name = "Caffeine", units = "mg", description = "Synthetic local subject"))
            tag("Synthetic group")
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val substances = SubstanceRepository(context, SubstanceParser())
        lateinit var model: TagSubjectsViewModel
        compose.runOnUiThread {
            model = TagSubjectsViewModel(repo, guard, db, substances, SavedStateHandle(mapOf("tagId" to tag.id)))
            models.put("bulk", model)
        }
        compose.waitUntil(15000) { model.ready || model.error != null }
        assertTrue(model.error, model.ready)
        val saved = AtomicInteger()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PsyZineTheme { TagSubjectsScreen(back = { saved.incrementAndGet() }, model = model) }
            }
        }
        compose.onNodeWithText("Search subjects").performTextInput("Caffeine")
        compose.waitUntil(15000) { model.filtered.value.size == 2 }
        compose.onNodeWithTag("tag-subject-list").performScrollToNode(hasTestTag("tag-subject:pw:Caffeine"))
        compose.onNodeWithTag("tag-subject:pw:Caffeine").performClick()
        compose.onNodeWithTag("tag-subject-list").performScrollToNode(hasTestTag("tag-subject:local:Caffeine"))
        compose.onNodeWithTag("tag-subject:local:Caffeine").performClick()
        compose.onNodeWithText("Save subjects").performClick()
        compose.waitUntil(15000) { saved.get() > 0 || model.error != null }
        assertNull(model.error); assertEquals(1, saved.get())
        assertEquals(setOf("pw:Caffeine", "local:Caffeine"), runBlocking { db.definitionDao().memberships().map { it.subjectId }.toSet() })
    }
}
