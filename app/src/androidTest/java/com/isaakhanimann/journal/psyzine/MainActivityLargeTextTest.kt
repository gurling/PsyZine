// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.MainActivity
import org.junit.*
import org.junit.Assert.*
import java.io.File

class MainActivityLargeTextTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationLabelsAndSettingsRemainUsableWithPlatformScaling() {
        Assume.assumeTrue("Uses emulator onboarding preferences only", Build.HARDWARE in listOf("ranchu", "goldfish"))
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS
            )
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
        compose.waitUntil(60000) { compose.onAllNodes(isSelectable() and hasText("Journal")).fetchSemanticsNodes().isNotEmpty() }
        for (label in listOf("Journal", "Stats", "Drugs", "Safer", "Settings")) {
            val matcher = isSelectable() and hasText(label)
            val tab = compose.onNode(matcher)
            if (compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) tab.performScrollTo()
            tab.performClick().assertIsSelected()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "main-large-text-captures").apply { mkdirs() }
            val screenshot = File(directory, "$label.png")
            val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.waitForIdle()
            if (!device.takeScreenshot(screenshot)) {
                screenshot.outputStream().use {
                    assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            val labels = compose.onAllNodes(hasText(label) and hasAnyAncestor(isSelectable()) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            val nodes = labels.fetchSemanticsNodes()
            assertTrue("Missing navigation label $label", nodes.isNotEmpty())
            nodes.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
                layouts.forEach { layout ->
                    assertTrue("Clipped navigation label $label: ${layout.size}", layout.size.width > 0 && layout.size.height > 0)
                    assertEquals("Navigation label should stay on one line: $label", 1, layout.lineCount)
                    assertFalse("Navigation label truncated: $label", layout.isLineEllipsized(0))
                }
            }

        }
        compose.onNodeWithText("Independent substance heights").performScrollTo().assertIsDisplayed()
    }
}
