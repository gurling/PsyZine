// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.MainActivity
import org.junit.*

class TagNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun readyApp() {
        Assume.assumeTrue("Synthetic emulator navigation only", Build.HARDWARE in listOf("ranchu", "goldfish"))
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.waitUntil(60000) {
            compose.onAllNodes(isSelectable() and hasText("Journal")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty()) {
            val count = compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size
            repeat(count) { compose.onAllNodes(isToggleable())[it].performScrollTo().performClick() }
            compose.onNodeWithText("Continue").performScrollTo().performClick()
        }
        compose.waitUntil(60000) { compose.onAllNodes(isSelectable() and hasText("Drugs")).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tab(name: String): SemanticsNodeInteraction {
        val matcher = isSelectable() and hasText(name)
        val node = compose.onNode(matcher)
        if (compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        return node
    }
    @Test fun sharedTagScreensRetainTheirOriginAndRootReselectionWorks() {
        readyApp()
        tab("Drugs").performClick()
        compose.onNodeWithText("Manage tags").performClick()
        tab("Drugs").assertIsSelected()
        compose.onNodeWithText("New tag").performClick()
        compose.onNodeWithTag("tag-name").performTextInput("Unsaved navigation fixture")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        tab("Drugs").assertIsSelected().performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithTag("tag-name").assertDoesNotExist()
        tab("Settings").performClick()
        compose.onNodeWithText("Custom units").performScrollTo().performClick()
        compose.onNodeWithText("Manage tags").performClick()
        tab("Settings").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Custom substances & blends").assertIsDisplayed()
        tab("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }
    @Test fun toleranceNavigationKeepsStatisticsAsTheOrigin() {
        readyApp()
        tab("Stats").performClick()
        compose.onNodeWithText("Tolerance").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasText("Your recorded ingestions.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        tab("Stats").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Statistics").assertIsDisplayed()
        compose.onNodeWithText("Tolerance").assertIsDisplayed()
    }
    @Test fun newDefinitionEditorPreservesDrugsAndSettingsOrigins() {
        readyApp()
        tab("Drugs").performClick()
        compose.onNodeWithText("New definition").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("definition-form")).fetchSemanticsNodes().isNotEmpty() }
        tab("Drugs").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("New definition").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("definition-form")).fetchSemanticsNodes().isNotEmpty() }
        tab("Drugs").performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        tab("Settings").performClick()
        compose.onNodeWithText("Custom units").performScrollTo().performClick()
        compose.onNodeWithText("New definition").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("definition-form")).fetchSemanticsNodes().isNotEmpty() }
        tab("Settings").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Custom substances & blends").assertIsDisplayed()
    }
}
