// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.*
import com.isaakhanimann.journal.ui.tabs.search.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class SubjectSearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var repo: TagRepository
    private lateinit var guard: JournalSessionGuard
    private lateinit var generation: String
    private lateinit var substances: SubstanceRepository
    private val models = ViewModelStore()
    private var sequence = 0
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeTrue("Synthetic emulator UI fixture only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(db, lock); repo = TagRepository(db, guard)
        generation = guard.readSession(null) { it }
        substances = SubstanceRepository(context, SubstanceParser()); substances.initialize()
        db.experienceDao().insert(CustomSubstance(name = "Caffeine", units = "mg", description = "Synthetic"))
    }
    @After fun cleanup() {
        if (::db.isInitialized) { compose.runOnUiThread { models.clear() }; db.close() }
    }
    private fun model(state: SavedStateHandle): SearchViewModel {
        lateinit var model: SearchViewModel
        compose.runOnUiThread {
            model = SearchViewModel(substances, SubjectBrowserRepository(db, guard), state)
            models.put("search${sequence++}", model)
        }
        return model
    }
    private fun show(model: SearchViewModel, rtl: Boolean = false, definitionTap: (Int, String?) -> Unit = { _, _ -> }) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PsyZineTheme { SearchScreen(model, onSubstanceTap = {}, onCustomSubstanceTap = {},
                    navigateToAddCustomSubstanceScreen = {}, manageTags = {}, subjectTags = {}, onDefinitionTap = definitionTap) }
            }
        }
    }
    @Test fun tagAndUntaggedFiltersDistinguishSameNameSubjectsAndPersistState() {
        val tag = SubstanceTag(UUID.randomUUID().toString(), "Synthetic tagged group", "BLUE")
        runBlocking {
            repo.save(generation, tag, null)
            repo.assign(generation, SubjectId.Catalog("Caffeine"), setOf(tag.id), tag.id)
        }
        val state = SavedStateHandle(mapOf("subjectQuery" to "Caffeine"))
        val model = model(state); show(model)
        compose.waitUntil(15000) { model.results.value.catalogNames.size == 1 && model.results.value.locals.size == 1 }
        // The editable field remains exposed to accessibility at 200% font.
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNode(hasText(tag.name) and hasClickAction()).performClick()
        compose.waitUntil(15000) { model.results.value.catalogNames.size == 1 && model.results.value.locals.isEmpty() }
        compose.onNode(hasText("Untagged") and hasClickAction()).performClick()
        compose.waitUntil(15000) { model.results.value.locals.size == 1 }
        compose.runOnIdle { model.onFilterTapped("custom") }
        compose.waitUntil(15000) { model.results.value.catalogNames.isEmpty() && model.results.value.locals.size == 1 }
        val recovered = model(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        compose.waitUntil(15000) { recovered.tagState.value.snapshot != null }
        assertEquals("Caffeine", recovered.searchTextFlow.value)
        assertEquals(listOf(tag.id), recovered.selectedTags.value); assertTrue(recovered.includeUntagged.value)
        assertEquals(listOf("custom"), state.get<List<String>>("subjectCategories"))
    }
    @Test fun restoreClearsStaleTagIdsWhileKeepingTheQueryAndCategories() {
        val tag = SubstanceTag(UUID.randomUUID().toString(), "Synthetic", "BLUE")
        runBlocking { repo.save(generation, tag, null) }
        val state = SavedStateHandle(mapOf("subjectQuery" to "Caffeine", "subjectCategories" to listOf("stimulant"),
            "subjectTags" to listOf(tag.id), "subjectUntagged" to true, "subjectTagGeneration" to generation))
        val model = model(state); show(model)
        compose.waitUntil(15000) { model.tagState.value.snapshot != null }
        runBlocking { db.experienceDao().deleteEverythingAndInvalidate(UUID.randomUUID().toString()) }
        compose.waitUntil(15000) { model.tagState.value.snapshot?.generation != generation && model.selectedTags.value.isEmpty() }
        assertFalse(model.includeUntagged.value)
        assertEquals("Caffeine", model.searchTextFlow.value)
        assertEquals(listOf("stimulant"), state.get<List<String>>("subjectCategories"))
    }
    @Test fun nativeDefinitionSearchIntersectsLeafAndNoteTermsAndUsesOwnTagsAtLargeText() {
        val name = "Synthetic long named archived composition for readable search results"
        val tag = SubstanceTag(UUID.randomUUID().toString(), "Synthetic selected tag", "BLUE")
        runBlocking {
            db.experienceDao().insert(com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit(
                id = 1, substanceName = null, name = name, administrationRoute = null, dose = null,
                estimatedDoseStandardDeviation = null, isEstimate = false, isArchived = true, unit = "capsule",
                originalUnit = null, note = "Synthetic marker", isHiddenInTimeline = true))
            db.definitionDao().insertComponents(listOf(DoseComponent(1, 1, substanceName = "Caffeine", isHiddenInTimeline = true)))
            repo.save(generation, tag, null)
            repo.assign(generation, SubjectId.Catalog("Caffeine"), setOf(tag.id), tag.id)
        }
        val model = model(SavedStateHandle(mapOf("subjectQuery" to "caff,synthetic")))
        var opened: Pair<Int, String?>? = null
        show(model, rtl = true) { id, token -> opened = id to token }
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
        compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Archived definition").assertIsDisplayed()
        val capture = "/sdcard/Download/psyzine-ui-check-${UUID.randomUUID()}"
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("mkdir -p $capture")
        device.executeShellCommand("screencap -p $capture/browser-200-rtl.png")
        println("PsyZine browser screenshot: $capture/browser-200-rtl.png")
        compose.onNodeWithText(name).performClick()
        compose.runOnIdle { assertEquals(1 to generation, opened) }
        compose.runOnIdle { model.toggleTag(tag.id) }
        compose.waitUntil(15000) { model.results.value.definitions.isEmpty() }
        compose.runOnIdle { model.untagged(true) }
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
        runBlocking { repo.assign(generation, SubjectId.Definition(1), setOf(tag.id), tag.id) }
        compose.runOnIdle { model.untagged(false); model.onFilterTapped("stimulant") }
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
        compose.runOnIdle { model.filterSubstances("caff,unrelated") }
        compose.waitUntil(15000) { model.results.value.definitions.isEmpty() }
    }
    @Test fun ingredientQueryRespectsCatalogTagsWhileOwnNameAndBlankQueryRemainIndependent() {
        val tag = SubstanceTag(UUID.randomUUID().toString(), "Synthetic own tag only", "BLUE")
        runBlocking {
            db.experienceDao().insert(com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit(
                id = 1, substanceName = null, name = "Native blend", administrationRoute = null, dose = null,
                estimatedDoseStandardDeviation = null, isEstimate = false, isArchived = false, unit = "capsule",
                originalUnit = null, note = ""))
            db.definitionDao().insertComponents(listOf(DoseComponent(1, 1, substanceName = "Caffeine")))
            repo.save(generation, tag, null)
            repo.assign(generation, SubjectId.Definition(1), setOf(tag.id), tag.id)
        }
        val model = model(SavedStateHandle(mapOf("subjectQuery" to "native", "subjectTags" to listOf(tag.id))))
        show(model)
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
        compose.runOnIdle { model.filterSubstances("caff") }
        compose.waitUntil(15000) { model.results.value.definitions.isEmpty() }
        compose.runOnIdle { model.filterSubstances("") }
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
        compose.runOnIdle { model.onFilterTapped("stimulant") }
        compose.waitUntil(15000) { model.results.value.definitions.isEmpty() }
        compose.runOnIdle { model.untagged(true) }
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
        runBlocking { repo.assign(generation, SubjectId.Catalog("Caffeine"), setOf(tag.id), tag.id) }
        compose.runOnIdle { model.untagged(false); model.filterSubstances("nonmatching barrier") }
        compose.waitUntil(15000) { model.results.value.definitions.isEmpty() }
        compose.runOnIdle { model.filterSubstances("caff") }
        compose.waitUntil(15000) { model.results.value.definitions.size == 1 }
    }
    @Test fun recordScopesRestoreAndReplaceResultsAtLargeText() {
        runBlocking {
            db.experienceDao().insert(com.isaakhanimann.journal.data.room.experiences.entities.Experience(1,
                "Synthetic searchable favorite", "", java.time.Instant.EPOCH, java.time.Instant.EPOCH, true, null))
            db.experienceDao().insert(com.isaakhanimann.journal.data.room.experiences.entities.Ingestion(1, "Caffeine", java.time.Instant.EPOCH,
                administrationRoute = com.isaakhanimann.journal.data.substances.AdministrationRoute.ORAL, dose = null,
                isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg", experienceId = 1,
                notes = "Synthetic searchable note", stomachFullness = null, consumerName = null, customUnitId = null))
        }
        val state = SavedStateHandle(mapOf("subjectQuery" to "synthetic searchable", "subjectScope" to JournalSearchScope.FAVORITES.name))
        val model = model(state)
        show(model, rtl = true)
        compose.waitUntil(15000) { model.results.value.experiences.size == 1 }
        compose.onNodeWithText("Synthetic searchable favorite").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { model.changeScope(JournalSearchScope.INGESTIONS) }
        compose.waitUntil(15000) { model.results.value.request.scope == JournalSearchScope.INGESTIONS && model.results.value.ingestions.size == 1 }
        assertTrue(model.results.value.experiences.isEmpty())
        compose.onNodeWithText("Unknown amount").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { model.filterSubstances("nonmatching"); model.filterSubstances("searchable note") }
        compose.waitUntil(15000) { model.results.value.request.query == "searchable note" && model.results.value.ingestions.size == 1 }
        val recovered = model(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        compose.waitUntil(15000) { recovered.tagState.value.snapshot != null }
        assertEquals(JournalSearchScope.INGESTIONS.name, recovered.scope.value)
        assertEquals("searchable note", recovered.searchTextFlow.value)
    }
}
