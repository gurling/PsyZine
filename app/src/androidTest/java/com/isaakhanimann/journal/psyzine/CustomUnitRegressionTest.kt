// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.ui.tabs.settings.customunits.*
import com.isaakhanimann.journal.ui.tabs.settings.customunits.add.*
import com.isaakhanimann.journal.ui.tabs.settings.customunits.edit.*
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.customunit.*
import com.isaakhanimann.journal.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.Instant
import java.util.UUID

class CustomUnitRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var directory: File
    private lateinit var repo: ExperienceRepository
    private lateinit var guard: JournalSessionGuard
    private lateinit var substances: SubstanceRepository
    private val models = ViewModelStore()
    private var sequence = 0
    private val stored = CustomUnit.twoCBSample.copy(id = 6, creationDate = Instant.EPOCH, dose = 0.0001,
        estimatedDoseStandardDeviation = 1.234567, isEstimate = false, unitPlural = null)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "custom-unit-${UUID.randomUUID()}").apply { mkdirs() }
        val privateContext = object : ContextWrapper(context) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val lock = JournalMutationLock()
        guard = JournalSessionGuard(db, lock)
        repo = ExperienceRepository(db.experienceDao(), db.photoAttachmentDao(), AttachmentStorage(privateContext), lock, guard)
        substances = SubstanceRepository(context, SubstanceParser())
        runBlocking { substances.initialize(); db.experienceDao().insert(stored) }
    }
    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
        db.close(); directory.deleteRecursively()
    }
    private fun editor(state: SavedStateHandle = SavedStateHandle(mapOf("customUnitId" to 6))): EditCustomUnitViewModel {
        lateinit var model: EditCustomUnitViewModel
        compose.runOnIdle { model = EditCustomUnitViewModel(repo, substances, guard, state); models.put("edit${sequence++}", model) }
        compose.waitUntil(30000) { !model.loading }
        return model
    }
    private fun chooser(id: Int = 6): ChooseDoseCustomUnitViewModel {
        lateinit var model: ChooseDoseCustomUnitViewModel
        compose.runOnIdle { model = ChooseDoseCustomUnitViewModel(repo, substances, SavedStateHandle(mapOf("customUnitId" to id)), guard); models.put("choose${sequence++}", model) }
        compose.waitUntil(30000) { !model.loading }
        return model
    }
    private fun create(): FinishAddCustomUnitViewModel {
        lateinit var model: FinishAddCustomUnitViewModel
        compose.runOnIdle { model = FinishAddCustomUnitViewModel(repo, substances, guard,
            SavedStateHandle(mapOf("substanceName" to "Caffeine", "administrationRoute" to AdministrationRoute.ORAL))); models.put("add${sequence++}", model) }
        compose.waitUntil(30000) { !model.loading }
        return model
    }
    private fun sql(statement: String) = runBlocking { withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL(statement) } }
    @Test fun creationReadsRouteInputsBeforeCompositionSnapshotIsApplied() {
        // Hilt can create the editor during composition, before its state writes
        // become visible to the Room transaction's background dispatcher.
        val snapshot = androidx.compose.runtime.snapshots.Snapshot.takeMutableSnapshot()
        lateinit var model: FinishAddCustomUnitViewModel
        try {
            compose.runOnIdle {
                snapshot.enter {
                    model = FinishAddCustomUnitViewModel(repo, substances, guard,
                        SavedStateHandle(mapOf("substanceName" to "2C-B", "administrationRoute" to AdministrationRoute.INSUFFLATED)))
                    models.put("composition-add", model)
                }
            }
            compose.waitUntil(30000) { model.ready || model.saveError != null }
            assertNull(model.saveError)
            assertTrue(model.ready)
            assertEquals(substances.getSubstance("2C-B")!!.getRoa(AdministrationRoute.INSUFFLATED)!!.roaDose, model.roaDose)
            assertEquals("mg", model.originalUnit)
        } finally { snapshot.dispose() }
    }
    private fun useUnit() = runBlocking {
        db.experienceDao().insert(Experience(42, "fixture", "", sortDate = Instant.EPOCH, location = null))
        db.experienceDao().insert(Ingestion(1, stored.substanceName, Instant.EPOCH, administrationRoute = stored.administrationRoute,
            dose = 1.0, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = stored.unit,
            experienceId = 42, notes = null, stomachFullness = null, consumerName = null, customUnitId = 6))
    }
    @Test fun validationKeepsDraftOpenAndErrorsIdentifyTheField() {
        val model = editor()
        var dismissed = false
        compose.runOnIdle { model.onChangeOfDose("-2"); model.updateAndDismissAfter { dismissed = true } }
        assertEquals("Dose cannot be negative.", model.errors[CustomUnitField.DOSE])
        assertFalse(dismissed)
        runBlocking { assertEquals(stored, repo.getCustomUnit(6)) }
        compose.runOnIdle { model.onChangeOfDose("0,0001"); model.onChangeOfNote("note only"); model.updateAndDismissAfter { dismissed = true } }
        compose.waitUntil(30000) { dismissed }
        runBlocking {
            val record = repo.getCustomUnit(6)!!
            assertEquals(stored.dose!!.toRawBits(), record.dose!!.toRawBits())
            assertEquals(stored.estimatedDoseStandardDeviation!!.toRawBits(), record.estimatedDoseStandardDeviation!!.toRawBits())
            assertNull(record.unitPlural)
        }
    }
    @Test fun failedWriteKeepsOriginalBaselineAndRetryPreservesHiddenUncertainty() {
        val model = editor()
        sql("CREATE TRIGGER injected_update BEFORE UPDATE ON CustomUnit BEGIN SELECT RAISE(ABORT, 'private database detail'); END")
        var dismissed = false
        compose.runOnIdle { model.onChangeOfIsEstimate(true); model.onChangeOfNote("retry draft"); model.updateAndDismissAfter { dismissed = true } }
        compose.waitUntil(30000) { !model.isSaving && model.saveError != null }
        assertFalse(dismissed); assertFalse(model.saveError!!.contains("private")); assertEquals("retry draft", model.note)
        runBlocking { assertEquals(stored, repo.getCustomUnit(6)) }
        sql("DROP TRIGGER injected_update")
        compose.runOnIdle { model.dismissSaveError(); model.onChangeOfIsEstimate(false); model.updateAndDismissAfter { dismissed = true } }
        compose.waitUntil(30000) { dismissed }
        runBlocking { assertEquals(stored.estimatedDoseStandardDeviation!!.toRawBits(), repo.getCustomUnit(6)!!.estimatedDoseStandardDeviation!!.toRawBits()) }
    }
    @Test fun deletionChecksFreshUsageAndArchiveChangesOnlyStoredFlag() {
        val model = editor()
        assertEquals(0, model.numberOfIngestionsWithThisCustomUnit)
        useUnit()
        var dismissed = false
        compose.runOnIdle { model.onChangeOfName("unsaved draft"); model.deleteCustomUnit { dismissed = true } }
        compose.waitUntil(30000) { !model.isSaving && model.usedUnitCount != null }
        assertEquals(1, model.usedUnitCount); assertFalse(dismissed)
        compose.runOnIdle { model.archiveSavedUnit { dismissed = true } }
        compose.waitUntil(30000) { dismissed }
        runBlocking {
            assertEquals(stored.copy(isArchived = true), repo.getCustomUnit(6))
            assertEquals(6, repo.getIngestionFlow(1).first()!!.ingestion.customUnitId)
        }
    }
    @Test fun unusedUnitCanBeDeletedAndInvalidLegacyUnitCanBeArchivedWithoutRepair() {
        var deleted = false
        val first = editor()
        compose.runOnIdle { first.deleteCustomUnit { deleted = true } }
        compose.waitUntil(30000) { deleted }
        runBlocking { assertNull(repo.getCustomUnit(6)); db.experienceDao().insert(stored.copy(dose = -1.0, name = "")) }
        val legacy = editor()
        var archived = false
        compose.runOnIdle { legacy.archiveSavedUnit { archived = true } }
        compose.waitUntil(30000) { archived }
        runBlocking { assertEquals(stored.copy(dose = -1.0, name = "", isArchived = true), repo.getCustomUnit(6)) }
    }
    @Test fun recreatedDraftKeepsValuesButRestoreAndConcurrentEditsRejectOldSessions() {
        val state = SavedStateHandle(mapOf("customUnitId" to 6))
        val first = editor(state)
        compose.runOnIdle { first.onChangeOfName("draft label"); first.onChangeOfNote("draft note") }
        val recovered = editor(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        assertEquals("draft label", recovered.name); assertEquals("draft note", recovered.note)
        runBlocking { db.experienceDao().update(stored.copy(name = "concurrent label")) }
        var dismissed = false
        compose.runOnIdle { recovered.updateAndDismissAfter { dismissed = true } }
        compose.waitUntil(30000) { recovered.saveError != null }
        assertFalse(dismissed); assertTrue(recovered.saveError!!.contains("changed"))
        runBlocking { db.withTransaction { db.experienceDao().deleteEverythingAndInvalidate("restored"); db.experienceDao().insert(stored.copy(name = "restored")) } }
        val stale = editor(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        assertFalse(stale.canSave); assertTrue(stale.saveError!!.contains("restored"))
        runBlocking { assertEquals("restored", repo.getCustomUnit(6)!!.name) }
    }
    @Test fun unknownCreationAndDuplicateTapsProduceOneCommittedRecord() {
        val model = create()
        assertTrue(model.ready)
        var callbacks = 0
        var savedId = 0
        compose.runOnIdle { model.createSaveAndDismissAfter { callbacks++ } }
        assertTrue(model.errors.containsKey(CustomUnitField.NAME)); assertEquals(0, callbacks)
        compose.runOnIdle {
            model.onChangeOfName("Unknown coffee"); model.onChangeOfUnit("cup"); model.onChangeOfUnitPlural("")
            repeat(2) { model.createSaveAndDismissAfter { savedId = it
                runBlocking { withTimeout(5000) { guard.readSession(null) { assertNotNull(repo.getCustomUnit(savedId)) } } }
                callbacks++
            } }
        }
        compose.waitUntil(30000) { callbacks > 0 }
        assertEquals(1, callbacks)
        runBlocking { val record = repo.getCustomUnit(savedId)!!; assertNull(record.dose); assertNull(record.unitPlural); assertEquals("cups", record.getPluralizableUnit().plural) }
    }
    @Test fun missingChooserAndChangedDefinitionsGiveRecoveryInsteadOfNavigation() {
        val missing = chooser(999)
        assertFalse(missing.ready); assertTrue(missing.saveError!!.contains("deleted"))
        val selected = chooser()
        compose.runOnIdle { selected.onDoseTextChange("1") }
        runBlocking { db.experienceDao().update(stored.copy(dose = 2.0)) }
        var navigated = false
        compose.runOnIdle { selected.next { _, _, _, _, _, _ -> navigated = true } }
        compose.waitUntil(30000) { selected.saveError != null }
        assertFalse(navigated); assertTrue(selected.saveError!!.contains("changed"))
    }
    @Test fun chooserReportsOverflowAndUnknownDoseRemainsAnExplicitAction() {
        runBlocking { db.experienceDao().update(stored.copy(dose = Double.MAX_VALUE)) }
        val selected = chooser()
        var navigated = false
        compose.runOnIdle { selected.onDoseTextChange("2"); selected.next { _, _, _, _, _, _ -> navigated = true } }
        assertTrue(selected.errors[CustomUnitField.DOSE]!!.contains("too large")); assertFalse(navigated)
        compose.runOnIdle { selected.onDoseTextChange("not a number"); selected.next(unknown = true) { _, amount, uncertainty, estimate, _, _ ->
            assertNull(amount); assertNull(uncertainty); assertFalse(estimate); navigated = true
        } }
        compose.waitUntil(30000) { navigated }
    }
    @Test fun savedInvalidUncertaintyIsVisibleAndCanBeCorrectedWithoutChangingEstimateFlag() {
        runBlocking { db.experienceDao().update(stored.copy(estimatedDoseStandardDeviation = -1.0)) }
        val model = editor()
        assertTrue(model.showRetainedUncertainty)
        var dismissed = false
        compose.runOnIdle { model.updateAndDismissAfter { dismissed = true } }
        assertEquals("Uncertainty cannot be negative.", model.errors[CustomUnitField.UNCERTAINTY])
        compose.runOnIdle { model.onChangeOfEstimatedDoseDeviation("0.5"); model.updateAndDismissAfter { dismissed = true } }
        compose.waitUntil(30000) { dismissed }
        runBlocking { val record = repo.getCustomUnit(6)!!; assertFalse(record.isEstimate); assertEquals(0.5, record.estimatedDoseStandardDeviation!!, 0.0) }
    }
    @Test fun uiExplainsInvalidLabelsAndUnknownDoseWithoutHidingDone() {
        val model = editor()
        compose.runOnIdle { model.onChangeOfName(""); model.onChangeOfUnit(""); model.onChangeOfDose("") }
        compose.setContent { PsyZineTheme { EditCustomUnitScreen({}, model) } }
        compose.onNodeWithContentDescription("Done").assertExists().assertHasClickAction().performClick()
        compose.onNodeWithText("Enter a name for this custom unit.").assertIsDisplayed()
        compose.onNodeWithText("Enter a singular unit label, for example capsule.").assertExists()
        compose.onNodeWithText("Leave blank if the dose is unknown.").assertExists()
    }
    @Test fun chooserNextRemainsVisibleAndExplainsMissingQuantity() {
        val model = chooser()
        compose.setContent { PsyZineTheme { ChooseDoseCustomUnitScreen({ _, _, _, _, _, _, _, _, _ -> }, { _, _ -> }, model) } }
        compose.onNodeWithContentDescription("Next").assertExists().assertHasClickAction().performClick()
        compose.onNodeWithText("Enter an amount, or choose Log unknown dose.").assertIsDisplayed()
        compose.onNodeWithText("Log unknown dose").assertExists()
    }
    @Test fun listShowsFailedLoadAndRetryInsteadOfAnEmptyJournal() {
        var retries = 0
        compose.setContent { PsyZineTheme { CustomUnitsScreenContent(emptyList(), {}, {}, {}, "saved search", {},
            error = "Could not load custom units. Try again.", onRetry = { retries++ }) } }
        compose.onNodeWithText("No custom units found").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("saved search").assertExists()
    }
    @Test fun keyboardNextMovesThroughDistinctSingularAndPluralFields() {
        val model = editor()
        compose.setContent { PsyZineTheme { EditCustomUnitScreen({}, model) } }
        val name = compose.onNode(hasText("Name") and hasSetTextAction())
        val singular = compose.onNode(hasText("Unit singular") and hasSetTextAction())
        val plural = compose.onNode(hasText("Unit plural (optional)") and hasSetTextAction())
        name.performClick().performImeAction()
        singular.assertIsFocused().performImeAction()
        plural.assertIsFocused()
    }
    @Test fun selectedGenerationAndDefinitionAreCheckedAtFinalIngestionSave() = runBlocking<Unit> {
        val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = preferenceScope) { File(directory, "preferences.preferences_pb") }
        val preferences = com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences(store)
        try {
            val generation = guard.currentGeneration()
            fun finish(): com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.FinishIngestionScreenViewModel {
                lateinit var model: com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.FinishIngestionScreenViewModel
                compose.runOnIdle {
                    model = com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.FinishIngestionScreenViewModel(repo, preferences, guard,
                        SavedStateHandle(mapOf("administrationRoute" to stored.administrationRoute, "isEstimate" to false,
                            "units" to stored.unit, "dose" to 1.0, "estimatedDoseStandardDeviation" to null,
                            "substanceName" to stored.substanceName, "customUnitId" to 6,
                            "journalGeneration" to generation, "customUnitFingerprint" to stored.definitionFingerprint())))
                    models.put("finish${sequence++}", model)
                }
                compose.waitUntil(30000) { !model.isLoadingColor || model.saveError != null }
                return model
            }
            val model = finish()
            db.experienceDao().update(stored.copy(dose = 10.0))
            var dismissed = false
            compose.runOnIdle { model.createSaveAndDismissAfter { dismissed = true } }
            compose.waitUntil(30000) { model.saveError != null }
            assertFalse(dismissed); assertTrue(model.saveError!!.contains("changed"))
            db.withTransaction { db.experienceDao().deleteEverythingAndInvalidate("after-selection"); db.experienceDao().insert(stored) }
            val stale = finish()
            assertTrue(stale.saveError!!.contains("restored"))
            assertTrue(repo.getSortedExperiencesWithIngestionsFlow().first().isEmpty())
        } finally { preferenceScope.cancel() }
    }

    @Test fun inputErrorsRemainReadableAcrossPalettesLargeFontsAndRtl() {
        val model = editor()
        compose.runOnIdle { model.onChangeOfName(""); model.updateAndDismissAfter {} }
        var palette by mutableStateOf(ThemeId.MOCHA)
        var systemDark by mutableStateOf(false)
        val values = ThemeId.entries.map { it to false } + (ThemeId.SYSTEM to true)
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f),
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                PsyZineTheme(palette) { EditCustomUnitScreen({}, model) }
            }
        }
        val captures = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "custom-unit-captures").apply { mkdirs() }
        values.forEach { (value, dark) ->
            compose.runOnIdle { palette = value; systemDark = dark }
            compose.onNodeWithText("Enter a name for this custom unit.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Done").assertExists().assertHasClickAction()
            File(captures, "${value.key}-${if (dark) "dark" else "light"}-rtl-large.png").outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Test fun operationsRunOnceAndCancellationRollsBackWithoutAnErrorDialog() = runBlocking<Unit> {
        class GateEditor : CustomUnitEditorViewModel(guard, SavedStateHandle()) {
            init { load { populate(repo.getCustomUnit(6)!!) } }
            fun save(entered: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>, dismiss: () -> Unit) {
                operation("save this unit", dismiss) {
                    repo.update(stored.copy(note = "pending"))
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        lateinit var model: GateEditor
        val store = ViewModelStore()
        compose.runOnIdle { model = GateEditor(); store.put("gate", model) }
        compose.waitUntil(30000) { model.ready }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var callbacks = 0
        compose.runOnIdle { repeat(2) { model.save(entered, release) { callbacks++ } } }
        withTimeout(30000) { entered.await() }
        assertEquals(0, callbacks)
        compose.runOnIdle { store.clear() }
        withTimeout(30000) { guard.readSession(null) { assertEquals(stored, repo.getCustomUnit(6)) } }
        compose.waitUntil(30000) { !model.isSaving }
        assertNull(model.saveError); assertEquals(0, callbacks)
    }

    @Test fun failedCreateKeepsDraftAndRetryNavigatesAfterCommit() {
        val model = create()
        sql("CREATE TRIGGER injected_insert BEFORE INSERT ON CustomUnit BEGIN SELECT RAISE(ABORT, 'private insert failure'); END")
        var callbacks = 0
        compose.runOnIdle {
            model.onChangeOfName("Retained draft"); model.onChangeOfUnit("cup")
            model.createSaveAndDismissAfter { callbacks++ }
        }
        compose.waitUntil(30000) { model.saveError != null && !model.isSaving }
        assertEquals(0, callbacks); assertEquals("Retained draft", model.name); assertFalse(model.saveError!!.contains("private"))
        sql("DROP TRIGGER injected_insert")
        compose.runOnIdle { model.dismissSaveError(); model.createSaveAndDismissAfter { id ->
            runBlocking { withTimeout(5000) { guard.readSession(null) { assertEquals("Retained draft", repo.getCustomUnit(id)!!.name) } } }
            callbacks++
        } }
        compose.waitUntil(30000) { callbacks == 1 }
    }

    @Test fun missingUnitScreenExplainsRecoveryAndOffersClose() {
        val model = chooser(999)
        var closed = false
        compose.setContent { PsyZineTheme { ChooseDoseCustomUnitScreen({ _, _, _, _, _, _, _, _, _ -> }, { _, _ -> }, model, navigateBack = { closed = true }) } }
        compose.onNodeWithText("This custom unit was deleted. Go back and choose another unit.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }

    @Test fun changedUnitCannotOverflowDosesInExistingJournalEntries() {
        useUnit()
        runBlocking { db.experienceDao().update(repo.getIngestionFlow(1).first()!!.ingestion.copy(dose = 2.0)) }
        val model = editor()
        var dismissed = false
        compose.runOnIdle { model.onChangeOfDose(Double.MAX_VALUE.toString()); model.updateAndDismissAfter { dismissed = true } }
        compose.waitUntil(30000) { model.saveError != null && !model.isSaving }
        assertFalse(dismissed); assertTrue(model.saveError!!.contains("existing journal")); assertTrue(model.canSave)
        runBlocking { assertEquals(stored, repo.getCustomUnit(6)) }
    }

    @Test fun firstLoadFailureCanRetryWithoutBindingToRolledBackGeneration() {
        class RetryEditor : CustomUnitEditorViewModel(guard, SavedStateHandle()) {
            var attempts = 0
            init { load {
                if (attempts++ == 0) error("temporary read failure")
                populate(repo.getCustomUnit(6)!!)
            } }
        }
        lateinit var model: RetryEditor
        compose.runOnIdle { model = RetryEditor(); models.put("retry-load", model) }
        compose.waitUntil(30000) { !model.loading }
        assertFalse(model.ready); assertFalse(model.requiresReopen)
        runBlocking { assertNull(db.photoAttachmentDao().generation()) }
        compose.runOnIdle { model.retryLoad() }
        compose.waitUntil(30000) { !model.loading }
        assertTrue("Retry should recover: ${model.saveError}", model.ready)
        assertNull(model.saveError); assertEquals(2, model.attempts)
    }

    @Test fun correctingLegacyUncertaintyKeepsFieldVisibleWithoutTurningOnEstimate() {
        runBlocking { db.experienceDao().update(stored.copy(estimatedDoseStandardDeviation = -1.0)) }
        val model = editor()
        compose.setContent { PsyZineTheme { EditCustomUnitScreen({}, model) } }
        val field = compose.onNode(hasText("Uncertainty") and hasSetTextAction())
        field.performScrollTo().performTextClearance()
        field.assertExists().performTextInput("0.5")
        field.assertExists()
        assertFalse(model.isEstimate)
        compose.onNodeWithContentDescription("Done").performClick()
        compose.waitUntil(30000) { !model.isSaving }
        runBlocking { assertEquals(0.5, repo.getCustomUnit(6)!!.estimatedDoseStandardDeviation!!, 0.0) }
    }

    @Test fun correctingMissingMeasurementLabelKeepsFieldVisibleWhileTyping() {
        runBlocking { db.experienceDao().update(stored.copy(originalUnit = "")) }
        val model = editor()
        compose.setContent { PsyZineTheme { EditCustomUnitScreen({}, model) } }
        val field = compose.onNode(hasText("Units") and hasSetTextAction())
        field.performScrollTo().performTextInput("m")
        field.assertExists().performTextInput("g")
        assertEquals("mg", model.originalUnit)
    }

}
