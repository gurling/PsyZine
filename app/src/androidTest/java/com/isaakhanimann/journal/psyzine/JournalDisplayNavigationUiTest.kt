// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.MainActivity
import com.isaakhanimann.journal.data.definitions.DoseComponent
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

class JournalDisplayNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var database: AppDatabase? = null
    private var entryId: Int? = null
    private var definitionId: Int? = null
    private val original = linkedMapOf<String, Boolean>()
    private fun tab(name: String) {
        val matcher = isSelectable() and hasText(name)
        val node = compose.onNode(matcher)
        if (compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        node.performClick()
    }
    private fun setting(label: String) = compose.onNode(isToggleable() and hasText(label))
    private fun selected(label: String) = setting(label).fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
    private fun set(label: String, value: Boolean) {
        setting(label).performScrollTo()
        if (selected(label) != value) setting(label).performClick()
        compose.waitUntil(10000) { selected(label) == value }
    }
    private fun ready() {
        Assume.assumeTrue("Owned synthetic emulator only", Build.HARDWARE in listOf("ranchu", "goldfish"))
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.waitUntil(60000) {
            compose.onAllNodes(isSelectable() and hasText("Settings")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() ||
                compose.onAllNodes(hasText("Continue")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty()) {
            repeat(compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size) {
                compose.onAllNodes(isToggleable())[it].performScrollTo().performClick()
            }
            compose.onNodeWithText("Continue").performScrollTo().performClick()
        }
        compose.waitUntil(60000) { compose.onAllNodes(isSelectable() and hasText("Settings")).fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        if (original.isNotEmpty()) {
            tab("Settings")
            original.forEach { (label, value) -> set(label, value) }
        }
        database?.let { db ->
            runBlocking {
                entryId?.let { db.experienceDao().deleteEverythingOfExperience(it) }
                definitionId?.let { id ->
                    db.definitionDao().deleteComponents(id)
                    db.experienceDao().getCustomUnit(id)?.let { db.experienceDao().delete(it) }
                }
            }
        }
    }
    private fun chart() = compose.onNode(hasContentDescription("Estimated effect timeline", substring = true))
    private fun inspectHiddenChart() {
        compose.waitUntil(20000) { compose.onAllNodes(hasContentDescription("Estimated effect timeline", substring = true)).fetchSemanticsNodes().size == 1 }
        val action = chart().fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Move cursor later by 15 minutes" }
        compose.runOnIdle { assertTrue(action.action()) }
        compose.onNodeWithTag("timeline_readout").assertIsDisplayed()
        compose.onNodeWithTag("timeline-quantity-list").assertDoesNotExist()
    }
    @Test fun settingsPersistAcrossNavigationAndUpdateCardsAndBothTimelineScreens() {
        ready()
        val key = UUID.randomUUID().toString().take(8)
        val title = "Entry $key"
        val definitionName = "Definition $key"
        val db = compose.activity.database
        database = db
        runBlocking {
            val unit = CustomUnit(0, null, definitionName, Instant.EPOCH, null, null, null, false, false, "capsule", "capsules", null, "")
            val unitId = db.experienceDao().insert(unit).toInt().also { definitionId = it }
            db.definitionDao().insertComponents(listOf(DoseComponent(parentCustomUnitId = unitId, substanceName = "Caffeine", substanceUnit = "mg", dose = 10.0),
                DoseComponent(parentCustomUnitId = unitId, substanceName = "Pregabalin", substanceUnit = "mg", dose = 10.0)))
            val id = db.experienceDao().insert(Experience(0, title, "", sortDate = Instant.EPOCH, location = null)).toInt().also { entryId = it }
            db.experienceDao().insert(Ingestion(substanceName = null, time = Instant.EPOCH, administrationRoute = AdministrationRoute.ORAL,
                dose = 1.0, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "capsule", experienceId = id,
                notes = null, stomachFullness = null, consumerName = null, customUnitId = unitId))
        }
        tab("Settings")
        for (label in listOf("Show components in experience", "Hide timeline quantity details")) {
            setting(label).performScrollTo(); original[label] = selected(label)
        }
        set("Show components in experience", true)
        set("Hide timeline quantity details", true)
        captureJournalDisplay("persisted-display-settings", compose)
        tab("Drugs")
        compose.onNodeWithTag("journal-search-scopes").performScrollToNode(hasText("Experiences"))
        compose.onNode(hasText("Experiences") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(key)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(20000) { compose.onAllNodes(hasText("Caffeine, Pregabalin")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Caffeine, Pregabalin").assertIsDisplayed()
        tab("Settings"); set("Show components in experience", false); tab("Drugs")
        compose.onNode(hasSetTextAction()).assertTextContains(key)
        compose.waitUntil(20000) { compose.onAllNodes(hasText(definitionName)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Caffeine, Pregabalin").assertDoesNotExist()
        compose.onNodeWithText(definitionName).assertIsDisplayed()
        compose.onNodeWithText(title).performClick()
        inspectHiddenChart()
        captureJournalDisplay("entry-time-cursor-only", compose)
        compose.onNodeWithContentDescription("Expand timeline").performClick()
        compose.waitUntil(20000) { compose.onAllNodes(hasText(com.isaakhanimann.journal.ui.YOU)).fetchSemanticsNodes().isNotEmpty() }
        inspectHiddenChart()
        captureJournalDisplay("expanded-time-cursor-only", compose)
        assertEquals(1.0, runBlocking { db.experienceDao().getIngestionsWithCompanions(entryId!!).single().ingestion.dose }!!, 0.0)
    }
}
