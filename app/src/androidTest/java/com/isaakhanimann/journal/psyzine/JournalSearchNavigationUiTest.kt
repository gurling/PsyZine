// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.MainActivity
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

class JournalSearchNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var db: AppDatabase? = null
    private var createdId: Int? = null
    private fun tab(name: String): SemanticsNodeInteraction {
        val matcher = isSelectable() and hasText(name)
        val node = compose.onNode(matcher)
        if (compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        return node
    }
    private fun ready() {
        Assume.assumeTrue("Owned synthetic emulator only", Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        compose.waitUntil(60000) {
            compose.onAllNodes(isSelectable() and hasText("Journal")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty()) {
            repeat(compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size) {
                compose.onAllNodes(isToggleable())[it].performScrollTo().performClick()
            }
            compose.onNodeWithText("Continue").performScrollTo().performClick()
        }
        compose.waitUntil(60000) { compose.onAllNodes(isSelectable() and hasText("Drugs")).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun scope(name: String) {
        compose.onNodeWithTag("journal-search-scopes").performScrollToNode(hasText(name))
        compose.onNode(hasText(name) and hasClickAction()).performClick()
    }
    @After fun cleanup(): Unit = runBlocking {
        db?.let { database ->
            createdId?.let { database.experienceDao().deleteEverythingOfExperience(it) }
            database.close()
        }
    }
    @Test fun recordDetailsAndEditsReturnToSearchWithSavedScopeAndQuery() {
        ready()
        val key = UUID.randomUUID().toString().take(8)
        val title = "Local record $key"
        val note = "$title ingestion note"
        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, "experiences_db").build().also { db = it }
        val ingestionId = runBlocking {
            // Seed only this uniquely named fixture before the search ViewModel opens.
            val id = database.experienceDao().insert(Experience(0, title, "", Instant.EPOCH, Instant.EPOCH, true, null)).toInt()
            createdId = id
            database.experienceDao().insert(Ingestion(0, "Caffeine", Instant.EPOCH,
                administrationRoute = AdministrationRoute.ORAL, dose = 0.0001, isDoseAnEstimate = false,
                estimatedDoseStandardDeviation = null, units = "mg", experienceId = id, notes = note,
                stomachFullness = null, consumerName = null, customUnitId = null))
            database.experienceDao().getIngestionsWithCompanions(id).single().ingestion.id
        }
        tab("Drugs").performClick()
        scope("Experiences")
        compose.onNode(hasSetTextAction()).performTextInput(key)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(15000) { compose.onAllNodes(hasText(title) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Edit options")).fetchSemanticsNodes().isNotEmpty() }
        tab("Drugs").assertIsSelected()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("journal-search-scopes")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertTextContains(key)
        scope("Ingestions")
        compose.waitUntil(15000) { compose.onAllNodes(hasText("Caffeine") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Caffeine").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasText("Edit ingestion")).fetchSemanticsNodes().isNotEmpty() }
        tab("Drugs").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText(note)).performScrollTo().performTextReplacement("$title edited note")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Done icon").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("journal-search-scopes")).fetchSemanticsNodes().isNotEmpty() }
        tab("Drugs").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains(key)
        val saved = runBlocking { database.experienceDao().getIngestion(ingestionId)!! }
        assertEquals("$title edited note", saved.notes)
        assertEquals(0.0001.toRawBits(), saved.dose!!.toRawBits())
    }
}
